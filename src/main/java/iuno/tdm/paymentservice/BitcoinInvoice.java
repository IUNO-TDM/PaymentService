/**
 * Copyright 2016 TRUMPF Werkzeugmaschinen GmbH + Co. KG
 * Created by Hans-Peter Bock on 01.03.2017.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package iuno.tdm.paymentservice;

import com.google.common.base.Stopwatch;
import com.google.common.io.BaseEncoding;
import io.swagger.model.AddressValuePair;
import io.swagger.model.Invoice;
import io.swagger.model.State;
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;


/**
 * Invoices may be paid by either completing a BIP21 URI in one single transaction
 * or by completing all transfers in one single transaction.
 */

public class BitcoinInvoice {
    private final NetworkParameters params = Context.get().getParams();
    private long totalAmount = 0;
    private long transferAmount = 0;
    private UUID invoiceId;
    private String referenceId;
    private Date expiration;
    private Address payDirect; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    private Address payTransfers;
    private Logger logger;

    private KeyChainGroup group;
    private Wallet couponWallet;

    private BitcoinInvoiceCallbackInterface bitcoinInvoiceCallbackInterface = null;

    /**
     * This member will be set to the transaction that paid this invoice.
     */
    private Transaction payingTx;
    /**
     * This member will be set to the transaction that paid the transfers of this invoice.
     */
    private Transaction transferTx;

    class TransferPair {
        final Address address;
        final Coin targetValue;

        TransferPair(Address a, Coin target) {
            address = a;
            targetValue = target;
        }

        AddressValuePair getAddressValuePair() {
            return new AddressValuePair().address(address.toBase58()).coin(targetValue.getValue());
        }
    }

    /**
     * This member contains all address value pairs for transfer payments.
     * It does not contain any payments to the payment services own wallet.
     */
    private List<TransferPair> transfers = new Vector<>();

    private TransactionConfidence.Listener payingTransactionConfidenceListener =  new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
            if(bitcoinInvoiceCallbackInterface != null){
                State state = mapConfidenceToState(confidence);
                bitcoinInvoiceCallbackInterface.invoiceStateChanged(BitcoinInvoice.this, state);
            }
        }
    };


    class Coupon {
        final ECKey ecKey;
        long value;
        Map<Sha256Hash, Transaction> transactions = null;
        Coupon(ECKey ecKey) {
            this.ecKey = ecKey;
        }
    }

    Vector<Coupon> coupons = new Vector<>();

    AddressValuePair addCoupon(String key) throws IllegalStateException, IOException {
        if (isExpired()) throw new IllegalStateException("invoice is already expired");

        Coupon coupon = new Coupon(DumpedPrivateKey.fromBase58(params, key).getKey());
        final String pubKeyHash = coupon.ecKey.toAddress(params).toBase58();
        final String response = getUtxoString(pubKeyHash);
        logger.info(response);
        coupon.value = getSatoshisFromUtxoString(response);
        coupons.add(coupon);

        // add key and unspent transactions to wallet
        group.importKeys(coupon.ecKey);
        coupon.transactions = getTransactionsForUtxoString(response);
        for (final Transaction tx : coupon.transactions.values())
            couponWallet.addWalletTransaction(new WalletTransaction(WalletTransaction.Pool.UNSPENT, tx));

        return new AddressValuePair().address(pubKeyHash).coin(coupon.value);
    }

    public Wallet getCouponWallet() {
        return couponWallet;
    }

    /**
     * Tries to pay the invoice using coupons.
     * @return null or signed transaction ready for broadcasting
     */
    Transaction tryPayWithCoupons() {
        Transaction result = null;
        long balance = couponWallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE).getValue();
        if (null != payingTx) {
            logger.info("Transaction has already been paid");

        } else if (balance < totalAmount) {
            logger.info("Too few coupons in wallet: " + couponWallet.getBalance().toFriendlyString());

        } else {
            Transaction tx = new Transaction(params);
            addTransfersToTx(tx);
            SendRequest sr = SendRequest.forTx(tx);
            sr.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
            if (balance > (totalAmount + 2*Transaction.MIN_NONDUST_OUTPUT.getValue())) {
                sr.tx.addOutput(Coin.valueOf(totalAmount-transferAmount), payTransfers);
                sr.changeAddress = coupons.lastElement().ecKey.toAddress(params);
            } else {
                sr.changeAddress = payTransfers;
            }

            try {
                couponWallet.completeTx(sr); // TODO this is a race in case two invoices use the same (yet unfunded) coupon
                couponWallet.commitTx(sr.tx);
                result = sr.tx;
                payingTx = sr.tx;
                transferTx = sr.tx;
                payingTx.getConfidence().addEventListener(payingTransactionConfidenceListener);
            } catch (InsufficientMoneyException e) { // should never happen
                e.printStackTrace();
            }
        }

        return result;
    }

    static public String getUtxoString(String b58) throws IOException {
        URL url;
        String response = "";
        url = new URL("https://testnet.blockexplorer.com/api/addr/" + b58 + "/utxo");
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        String line;
        while ((line = in.readLine()) != null) {
            response += line;
        }
        return response;
    }

    private long getSatoshisFromUtxoString(String str) {
        final JSONArray json = new JSONArray(str);
        long value = 0;
        for (int i = 0; i < json.length(); i++) { // TODO two times the same for loop is inefficient
            final JSONObject jsonObject = json.getJSONObject(i);
            value += jsonObject.getLong("satoshis"); // satoshis
        }
        return value;
    }

    private Map<Sha256Hash, Transaction> getTransactionsForUtxoString(String str) {
        final JSONArray json = new JSONArray(str);

        logger.info("Array length: " + json.length());

        final Map<Sha256Hash, Transaction> transactions = new HashMap<>(json.length());

        for (int i = 0; i < json.length(); i++) { // TODO two times the same for loop is inefficient
            final JSONObject jsonObject = json.getJSONObject(i);
            final String txId = jsonObject.getString("txid");
            final Sha256Hash utxoHash = Sha256Hash.wrap(txId); // txid
            final int utxoIndex = jsonObject.getInt("vout"); // vout
            final byte[] utxoScriptBytes = BaseEncoding.base16().lowerCase().decode(
                    jsonObject.getString("scriptPubKey"));
            final Coin utxoValue = Coin.valueOf(jsonObject.getLong("satoshis")); // satoshis

            Transaction tx = transactions.get(utxoHash);
            if (tx == null) {
                tx = new FakeTransaction(params, utxoHash);
                tx.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
                transactions.put(utxoHash, tx);
            }

            final TransactionOutput output = new TransactionOutput(params, tx, utxoValue, utxoScriptBytes);

            if (tx.getOutputs().size() > utxoIndex) {
                // Work around not being able to replace outputs on transactions
                final List<TransactionOutput> outputs = new ArrayList<>(tx.getOutputs());
                final TransactionOutput dummy = outputs.set(utxoIndex, output);
                checkState(dummy.getValue().equals(Coin.NEGATIVE_SATOSHI),
                        "Index %s must be dummy output", utxoIndex);
                // Remove and re-add all outputs
                tx.clearOutputs();
                for (final TransactionOutput o : outputs)
                    tx.addOutput(o);
            } else {
                // Fill with dummies as needed
                while (tx.getOutputs().size() < utxoIndex)
                    tx.addOutput(new TransactionOutput(params, tx,
                            Coin.NEGATIVE_SATOSHI, new byte[]{}));

                // Add the real output
                tx.addOutput(output);
            }
        }
        return transactions;
    }

    private static class FakeTransaction extends Transaction {
        private final Sha256Hash hash;

        private FakeTransaction(final NetworkParameters params, final Sha256Hash hash) {
            super(params);
            this.hash = hash;
        }

        @Override
        public Sha256Hash getHash() {
            return hash;
        }
    }

    /**
     * This constructor checks a new invoice for sanity.
     * @param id unique id of invoice object
     * @param inv invoice as defined in restful api
     * @param addr address for incoming payment (likely the payments service own wallet)
     * @throws IllegalArgumentException thrown if provided invoice contains illegal values
     */
    BitcoinInvoice(UUID id, Invoice inv, Address addr, Address addr2, BitcoinInvoiceCallbackInterface callbackInterface, DeterministicSeed seed) throws IllegalArgumentException {
        logger = LoggerFactory.getLogger(Bitcoin.class);
        bitcoinInvoiceCallbackInterface = callbackInterface;
        // check sanity of invoice
        totalAmount = inv.getTotalAmount();
        if (totalAmount < Transaction.MIN_NONDUST_OUTPUT.getValue())
            throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

        // check values (transfer shall be lower than totalamount)
        for (AddressValuePair avp : inv.getTransfers()) {
            Address a = Address.fromBase58(params, avp.getAddress());
            long value = avp.getCoin();
            if (value < Transaction.MIN_NONDUST_OUTPUT.getValue())
                throw new IllegalArgumentException("transfer amount to " + avp.getAddress() + " is less than bitcoin minimum dust output");
            transferAmount += value;
            transfers.add(new TransferPair(a, Coin.valueOf(value)));
        }
        if (totalAmount < (transferAmount + Transaction.MIN_NONDUST_OUTPUT.getValue()))
            throw new IllegalArgumentException("total invoice amount minus sum of transfer amounts is dust");

        // expiration date shall be in the future
        expiration = inv.getExpiration();
        if (isExpired())
            throw new IllegalArgumentException("expiration date must be in the future");

        invoiceId = id;
        referenceId = inv.getReferenceId();
        payDirect = addr;
        payTransfers = addr2;

        Stopwatch watch = Stopwatch.createStarted();
        group = new KeyChainGroup(params, seed);
        group.setLookaheadSize(4);
        couponWallet = new Wallet(params, group);
        watch.stop();
        logger.info("wallet took {}", watch);

        couponWallet.allowSpendingUnconfirmedTransactions();
    }

    public Invoice getInvoice() {
        Invoice result = new Invoice()
                .totalAmount(totalAmount)
                .expiration(expiration)
                .invoiceId(invoiceId)
                .referenceId(referenceId);
        for (TransferPair pa : transfers)
            result.addTransfersItem(pa.getAddressValuePair());
        return result;
    }

    /**
     * Checks if the invoice is expired.
     * @return true if invoice is expired
     */
    boolean isExpired() {
        return (expiration.before(new Date()));
    }

    /**
     * Returns a BIP21 payment request string.
     * @return BIP21 payment request string
     */
    String getBip21URI() {
        return BitcoinURI.convertToBitcoinURI(payDirect, Coin.valueOf(totalAmount), "PaymentService", "all your coins belong to us");
    }

    /**
     * Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.
     * The address value pair of the own wallet is added to the transfers as well.
     * @return the address value/pairs to fulfill the invoice as array
     */
    List<AddressValuePair> getTransfers() {
        List<AddressValuePair> avpList = new Vector<>();
        for (TransferPair pa : transfers)
            avpList.add(pa.getAddressValuePair());

        long difference = totalAmount - transferAmount;
        avpList.add(new AddressValuePair().address(payTransfers.toBase58()).coin(difference));
        return avpList;
    }

    State getState() {
        TransactionConfidence confidence = null;
        if (null != payingTx) confidence = payingTx.getConfidence();
        return mapConfidenceToState(confidence);
    }

    static public State mapConfidenceToState(TransactionConfidence conf) {
        State result = new State();
        result.setState(State.StateEnum.UNKNOWN);
        result.setDepthInBlocks(Integer.MIN_VALUE);
        if (conf != null) {
            switch (conf.getConfidenceType()) {
                case BUILDING:
                    result.setState(State.StateEnum.BUILDING);
                    result.setDepthInBlocks(conf.getDepthInBlocks());
                    break;
                case PENDING:
                    result.setState(State.StateEnum.PENDING);
                    result.setDepthInBlocks(0);
                    break;
                case DEAD:
                    result.setState(State.StateEnum.DEAD);
                    result.setDepthInBlocks(Integer.MIN_VALUE);
                    break;
                case IN_CONFLICT:
                    result.setState(State.StateEnum.CONFLICT);
                    result.setDepthInBlocks(Integer.MIN_VALUE);
                    break;
                case UNKNOWN:
                default:
            }
        }

        return result;
    }

    Set<TransactionInput> getInputs() {
        Set<TransactionInput> inputs = new HashSet<>();
        for (TransactionOutput tout : payingTx.getOutputs()) {
            if (payDirect.equals(tout.getAddressFromP2PKHScript(params))
                    && tout.isAvailableForSpending()) {
                int index = tout.getIndex();
                TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, payingTx);
                byte[] script = tout.getScriptBytes();
                TransactionInput txin; // TODO check if script needs to contain something
                txin = new TransactionInput(params, payingTx, script, txOutpoint);
                txin.clearScriptBytes();
                inputs.add(txin);
            }
        }
        return inputs;
    }

    SendRequest tryFinishInvoice() {
        if (null == payingTx) {
            logger.info("Invoice " + invoiceId.toString() + " has not yet been paid.");
            return null;
        }

        if (null != transferTx) {
            logger.info("Invoice " + invoiceId.toString() + " is already finished.");
            return null;
        }

        Transaction tx = new Transaction(params);

        // add inputs from incoming payment
        for (TransactionInput txin : getInputs())
            tx.addInput(txin);

        addTransfersToTx(tx);

        tx.setMemo(invoiceId.toString());
        SendRequest sr = SendRequest.forTx(tx);
        transferTx = tx;
        logger.info(String.format("Forwarding transfers for invoice %s.", invoiceId.toString()));

        return sr;
    }

    private void addTransfersToTx(Transaction tx) {
        // add transfer outputs
        for (TransferPair pa : transfers) {
            tx.addOutput(pa.targetValue, pa.address);
        }
    }

    private boolean doesTxFulfillTransferPayment(HashMap<Address, Coin> foo) {
        boolean result = true;

        if (( ! foo.keySet().contains(payTransfers)) // own wallet must be paid
                || (totalAmount-transferAmount) > foo.get(payTransfers).getValue()) {
            result = false;
        } else {
            for (TransferPair pa : transfers) { // all transfers must be paid as well
                if ( ! (foo.keySet().contains(pa.address))
                        || (pa.targetValue.isGreaterThan(foo.get(pa.address)))) {
                    result = false;
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     * @deprecated this is an inefficient way that only works for verifying just a few payments per second
     * @param tx new transaction with outputs to be checked
     */
    public void sortOutputsToAddresses(Transaction tx, HashMap<Address, Coin> foo) {

        if (foo.keySet().contains(payDirect)) {
            long value = foo.get(payDirect).getValue();
            if (totalAmount <= value) { // transaction fulfills direct payment
                logger.info("Received direct payment for invoice " + invoiceId.toString()
                        + " to " + payDirect
                        + " with " + foo.get(payDirect).toFriendlyString());
                payingTx = tx;
                payingTx.getConfidence().addEventListener(payingTransactionConfidenceListener);
                payingTransactionConfidenceListener.onConfidenceChanged(payingTx.getConfidence(),null);
            }

        } else if (foo.keySet().contains(payTransfers)) {
            if (doesTxFulfillTransferPayment(foo)) {
                logger.info("Received transfer payment for invoice " + invoiceId.toString()
                        + " to " + payTransfers);
                payingTx = tx;
                transferTx = tx;
                payingTx.getConfidence().addEventListener(payingTransactionConfidenceListener);
            }
        }
    }
}
