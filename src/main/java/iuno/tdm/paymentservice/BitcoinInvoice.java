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
import io.swagger.model.*;
import org.bitcoinj.core.*;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.Utils.HEX;

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
    private Address receiveAddress; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    private Address transferAddress;
    private static final Logger logger = LoggerFactory.getLogger(BitcoinInvoice.class);

    public Address getReceiveAddress() {
        return receiveAddress;
    }

    public Address getTransferAddress() {
        return transferAddress;
    }

    private KeyChainGroup group;
    private Wallet couponWallet;

    private BitcoinInvoiceCallbackInterface bitcoinInvoiceCallbackInterface = null;

    private TransactionList incomingTxList = new TransactionList();

    private TransactionList transferTxList = new TransactionList();

    public io.swagger.model.PaymentInformation getPaymentInformation() {
        return new io.swagger.model.PaymentInformation()
                .pcPubKey("")
                .pcInvoiceId("")
                .coin(totalAmount)
                .bitcoinAddress(receiveAddress.toBase58());
    }

    class PaymentInformation {
        final Address bitcoinAddress;
        final Coin targetValue;
        final String pcPubKey;
        final String pcIncoiceId;

        public PaymentInformation(Address bitcoinAddress, Coin targetValue, String pcPubKey, String pcIncoiceId) {
            this.bitcoinAddress = bitcoinAddress;
            this.targetValue = targetValue;
            this.pcPubKey = pcPubKey;
            this.pcIncoiceId = pcIncoiceId;
        }

        io.swagger.model.PaymentInformation getPaymentInformation() {
            return new io.swagger.model.PaymentInformation()
                    .bitcoinAddress(bitcoinAddress.toBase58())
                    .coin(targetValue.getValue())
                    .pcInvoiceId(pcIncoiceId)
                    .pcPubKey(pcPubKey);
        }

        AddressValuePair getAddressValuePair(){
            return new AddressValuePair().address(bitcoinAddress.toBase58()).coin(targetValue.getValue());
        }
    }

    /**
     * This member contains all address value pairs for transfer payments.
     * It does not contain any payments to the payment services own wallet.
     */
    private List<PaymentInformation> transfers = new Vector<>();

    private TransactionListStateListener incomingTxStateListener = new TransactionListStateListener() {
        @Override
        public void mostConfidentTxStateChanged(Sha256Hash txHash, State state) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.invoiceStateChanged(BitcoinInvoice.this, state);
                logger.info(String.format("%s incoming tx %s changed state to (%s, %d)",
                        invoiceId,
                        txHash,
                        state.getState(),
                        state.getDepthInBlocks()));
            }
        }

        @Override
        public void transactionsOrStatesChanged(Transactions transactions) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.invoicePayingTransactionsChanged(BitcoinInvoice.this, transactions);
                logger.info(String.format("%s transaction count or state changed: Count %d",
                        invoiceId,
                        transactions.size()));
            }
        }
    };

    private TransactionListStateListener transferTxStateListener = new TransactionListStateListener() {
        @Override
        public void mostConfidentTxStateChanged(Sha256Hash txHash, State state) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.invoiceTransferStateChanged(BitcoinInvoice.this, state);
                logger.info(String.format("%s transfer tx %s changed state to (%s, %d)",
                        invoiceId,
                        txHash,
                        state.getState(),
                        state.getDepthInBlocks()));
            }
        }

        @Override
        public void transactionsOrStatesChanged(Transactions transactions) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.invoiceTransferTransactionsChanged(BitcoinInvoice.this, transactions);
                logger.info(String.format("%s transaction count or state changed: Count %d",
                        invoiceId,
                        transactions.size()));
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
    Vector<String> keys = new Vector<>();

    AddressValuePair addCoupon(String key) throws IllegalStateException, IOException {
        if (isExpired()) throw new IllegalStateException("invoice is already expired");

        if (keys.contains(key)) throw new IllegalStateException("coupon was already added");
        keys.add(key);

        Coupon coupon = new Coupon(DumpedPrivateKey.fromBase58(params, key).getKey());
        final String pubKeyHash = coupon.ecKey.toAddress(params).toBase58();
        final String response = getUtxoString(pubKeyHash);
        logger.debug(response);
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
     *
     * @return null or signed transaction ready for broadcasting
     */
    Transaction tryPayWithCoupons() {
        Transaction result = null;
        long balance = couponWallet.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE).getValue();
        if (incomingTxList.isOneOrMoreTxPending()) {
            logger.info(String.format("Transaction %s has already been paid.", invoiceId.toString()));

        } else if (balance < totalAmount) {
            logger.info("Too few coupons in wallet: " + couponWallet.getBalance().toFriendlyString());

        } else {
            Transaction tx = new Transaction(params);
            addTransfersToTx(tx);
            SendRequest sr = SendRequest.forTx(tx);
            sr.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
            if (balance > (totalAmount + 2 * Transaction.MIN_NONDUST_OUTPUT.getValue())) {
                sr.tx.addOutput(Coin.valueOf(totalAmount - transferAmount), transferAddress);
                sr.changeAddress = coupons.lastElement().ecKey.toAddress(params);
            } else {
                sr.changeAddress = transferAddress;
            }

            try {
                couponWallet.completeTx(sr); // TODO this is a race in case two invoices use the same (yet unfunded) coupon
                couponWallet.commitTx(sr.tx);
                result = sr.tx;
                incomingTxList.add(sr.tx);
                if (!transfers.isEmpty()) transferTxList.add(sr.tx);
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
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            response += line;
        }
        con.disconnect();
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
     *
     * @param id   unique id of invoice object
     * @param inv  invoice as defined in restful api
     * @param addr address for incoming payment (likely the payments service own wallet)
     * @throws IllegalArgumentException thrown if provided invoice contains illegal values
     */
    BitcoinInvoice(UUID id, Invoice inv, Address addr, Address addr2, BitcoinInvoiceCallbackInterface callbackInterface, DeterministicSeed seed) throws IllegalArgumentException {
        bitcoinInvoiceCallbackInterface = callbackInterface;
        incomingTxList.addStateListener(incomingTxStateListener);
        transferTxList.addStateListener(transferTxStateListener);
        // check sanity of invoice
        totalAmount = inv.getTotalAmount();
        if (totalAmount < Transaction.MIN_NONDUST_OUTPUT.getValue())
            throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

        // check values (transfer shall be lower than totalamount)
        for (io.swagger.model.PaymentInformation paymentInformation : inv.getTransfers()) {
            Address a = Address.fromBase58(params, paymentInformation.getBitcoinAddress());
            long value = paymentInformation.getCoin();
            if (value < Transaction.MIN_NONDUST_OUTPUT.getValue())
                throw new IllegalArgumentException("transfer amount to " + paymentInformation.getBitcoinAddress() + " is less than bitcoin minimum dust output");
            transferAmount += value;
            transfers.add(new PaymentInformation(a, Coin.valueOf(value),paymentInformation.getPcPubKey(),paymentInformation.getPcInvoiceId()));
        }
        if (totalAmount < (transferAmount + Transaction.MIN_NONDUST_OUTPUT.getValue()))
            throw new IllegalArgumentException("total invoice amount minus sum of transfer amounts is dust");

        // expiration date shall be in the future
        expiration = inv.getExpiration();
        if (isExpired())
            throw new IllegalArgumentException("expiration date must be in the future");

        invoiceId = id;
        referenceId = inv.getReferenceId();
        receiveAddress = addr;
        transferAddress = addr2;

        Stopwatch watch = Stopwatch.createStarted();
        group = new KeyChainGroup(params, seed);
        group.setLookaheadSize(4);
        couponWallet = new Wallet(params, group);
        watch.stop();
        logger.info("wallet took {}", watch);

        couponWallet.allowSpendingUnconfirmedTransactions();
    }

    /**
     * Returns the invoice data.
     *
     * @return invoice data
     */
    public Invoice getInvoice() {
        Invoice result = new Invoice()
                .totalAmount(totalAmount)
                .expiration(expiration)
                .invoiceId(invoiceId)
                .referenceId(referenceId);
        for (PaymentInformation pa : transfers)
            result.addTransfersItem(pa.getPaymentInformation());
        return result;
    }

    /**
     * Checks if the invoice is expired.
     *
     * @return true if invoice is expired
     */
    boolean isExpired() {
        return (expiration.before(new Date()));
    }

    /**
     * Returns a BIP21 payment request string.
     *
     * @return BIP21 payment request string
     */
    String getBip21URI() {
        return BitcoinURI.convertToBitcoinURI(receiveAddress, Coin.valueOf(totalAmount), "PaymentService", "all your coins belong to us");
    }

    /**
     * Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.
     * The address value pair of the own wallet is added to the transfers as well.
     *
     * @return the address value/pairs to fulfill the invoice as array
     */
    List<AddressValuePair> getTransfers() {
        List<AddressValuePair> avpList = new Vector<>();
        for (PaymentInformation pa : transfers)
            avpList.add(pa.getAddressValuePair());

        long difference = totalAmount - transferAmount;
        avpList.add(new AddressValuePair().address(transferAddress.toBase58()).coin(difference));
        return avpList;
    }

    State getState() {
        return incomingTxList.getState();
    }

    State getTransferState() throws NoSuchFieldException {
        if (transfers.isEmpty()) {
            throw new NoSuchFieldException("TransactionState not applicable. No transfers for this invoice.");
        }
        return transferTxList.getState();
    }

    Transactions getPayingTransactions() {
        return incomingTxList.getTransactions();
    }

    Transactions getTransferTransactions() throws NoSuchFieldException {
        if (transfers.isEmpty()) {
            throw new NoSuchFieldException("getTransferTransactions not applicable. No transfers for this invoice.");
        }
        return transferTxList.getTransactions();
    }

    /**
     * Get all spendable outputs of the incoming transaction and return them as set of transaction inputs.
     *
     * @return set of TransactionInput
     */
    private Set<TransactionInput> getInputs() {
        Set<TransactionInput> inputs = new HashSet<>();
        Transaction relevantTx = incomingTxList.getMostConfidentTransaction();
        for (TransactionOutput tout : relevantTx.getOutputs()) {
            if (receiveAddress.equals(tout.getAddressFromP2PKHScript(params))
                    && tout.isAvailableForSpending()) {
                int index = tout.getIndex();
                TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, relevantTx);
                byte[] script = tout.getScriptBytes();
                TransactionInput txin; // TODO check if script needs to contain something
                txin = new TransactionInput(params, relevantTx, script, txOutpoint);
                txin.clearScriptBytes();
                inputs.add(txin);
            }
        }
        return inputs;
    }

    /**
     * This method tries to finish the invoice by creating a send request that pays the transfer payments.
     *
     * @return SendRequest object or null
     */
    SendRequest tryFinishInvoice() {
        if (transfers.isEmpty()) {
            logger.warn(String.format("Invoice %s has no transfers.", invoiceId.toString()));
            return null;
        }
        if (!incomingTxList.isOneOrMoreTxPending()) {
            logger.warn(String.format("Invoice %s has not yet been paid.", invoiceId.toString()));
            return null;
        }
        if (transferTxList.isOneOrMoreTxPending()) {
            logger.warn(String.format("Invoices %s transfers are already payed.", invoiceId.toString()));
            return null;
        }

        Transaction tx = new Transaction(params);

        // add inputs from incoming payment only if transaction is already included in a block to prevent malleability
        if (incomingTxList.isOneOrMoreTxConfirmed(0))
            for (TransactionInput txin : getInputs())
                tx.addInput(txin);

        addTransfersToTx(tx);

        tx.setMemo(invoiceId.toString());
        SendRequest sr = SendRequest.forTx(tx);
        transferTxList.add(tx);
        logger.debug(String.format("Forwarding transfers for invoice %s.", invoiceId.toString()));

        return sr;
    }

    private void addTransfersToTx(Transaction tx) {
        // add transfer outputs
        for (PaymentInformation pa : transfers) {
            tx.addOutput(pa.targetValue, pa.bitcoinAddress);
        }
    }

    private boolean doesTxFulfillTransferPayment(HashMap<Address, Coin> foo) {
        boolean result = true;

        if ((!foo.keySet().contains(transferAddress)) // own wallet must be paid
                || (totalAmount - transferAmount) > foo.get(transferAddress).getValue()) {
            result = false;
        } else {
            for (PaymentInformation pa : transfers) { // all transfers must be paid as well
                if (!(foo.keySet().contains(pa.bitcoinAddress))
                        || (pa.targetValue.isGreaterThan(foo.get(pa.bitcoinAddress)))) {
                    result = false;
                    break;
                }
            }
        }

        return result;
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     *
     * @param tx new transaction with outputs to be checked
     */
    public void sortOutputsToAddresses(Transaction tx, HashMap<Address, Coin> addressCoinHashMap) {
        logger.debug("transaction script: " + HEX.encode(tx.bitcoinSerialize()));

        if (addressCoinHashMap.keySet().contains(receiveAddress)) {
            long value = addressCoinHashMap.get(receiveAddress).getValue();
            if (totalAmount <= value) { // transaction fulfills direct payment
                logger.info("Received direct payment for invoice " + invoiceId.toString()
                        + " to " + receiveAddress
                        + " with " + addressCoinHashMap.get(receiveAddress).toFriendlyString());
                incomingTxList.add(tx);
//                if (transfers.isEmpty()) transferTx = tx; // no transfers so invoice is already complete
            }

        } else if (doesTxFulfillTransferPayment(addressCoinHashMap)) {
            logger.info("Received transfer payment for invoice " + invoiceId.toString()
                    + " to " + transferAddress);
            incomingTxList.add(tx);
            if (!transfers.isEmpty()) transferTxList.add(tx);

        } else {
            logger.warn(String.format("%s transaction %s contained no output for this invoice which should not happen",
                    invoiceId, tx.getHash().toString()));
        }

    }
}
