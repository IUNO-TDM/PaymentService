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
import io.swagger.model.Transactions;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.TransactionConfidenceEventListener;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.*;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
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
import static iuno.tdm.paymentservice.BitcoinInvoiceStateChangedEventListener.PAYMENTSTATECHANGE;
import static iuno.tdm.paymentservice.BitcoinInvoiceStateChangedEventListener.TRANSFERSTATECHANGE;
import static org.bitcoinj.core.Utils.HEX;

/**
 * Invoices may be paid by either completing a BIP21 URI in one single transaction
 * or by completing all transfers in one single transaction.
 */

public class BitcoinInvoice implements WalletChangeEventListener, TransactionConfidenceEventListener {
    private final NetworkParameters params = Context.get().getParams();
    private long totalAmount = 0;
    private long transferAmount = 0;
    private UUID invoiceId;
    private String referenceId;
    private Date expiration;
    private Address receiveAddress; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    private Address transferAddress;
    private static final Logger logger = LoggerFactory.getLogger(BitcoinInvoice.class);

    Address getReceiveAddress() {
        return receiveAddress;
    }

    Address getTransferAddress() {
        return transferAddress;
    }

    private KeyChainGroup group;
    private Wallet couponWallet;

    private BitcoinInvoiceStateChangedEventListener bitcoinInvoiceCallbackInterface = null;

    private TransactionList incomingTxList = new TransactionList();

    private TransactionList transferTxList = new TransactionList();

    private static String blockexplorerAddr = "https://test-insight.bitpay.com/api/";
    private static String blockexplorerUser = "";
    private static String blockexplorerPasswd = "";

    /**
     * works around delays due to tx floods by using the ouputs of an incoming payment transaction for an invoice
     * as inputs for te invoices transfer transaction
     * while allowing malleability to break the transfer transaction
     */
    private boolean useIncomingPaymentForTransfers;

    public void setUseIncomingPaymentForTransfers(boolean useIncomingPaymentForTransfers) {
        this.useIncomingPaymentForTransfers = useIncomingPaymentForTransfers;
    }

    private static final String PARAM_KEY_BE_ADDR = "blockexplorer-addr";
    private static final String PARAM_KEY_BE_USER = "blockexplorer-user";
    private static final String PARAM_KEY_BE_PASSWD = "blockexplorer-passwd";

    /**
     * This member contains all address value pairs for transfer payments.
     * It does not contain any payments to the payment services own wallet.
     */
    private List<TransferPair> transfers = new Vector<>();

    private TransactionListStateListener incomingTxStateListener = new TransactionListStateListener() {
        @Override
        public void mostConfidentTxStateChanged(Transaction tx, State state, Transactions txList) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.onInvoiceStateChanged(PAYMENTSTATECHANGE, BitcoinInvoice.this, state, tx, txList);
            }
        }
    };

    private TransactionListStateListener transferTxStateListener = new TransactionListStateListener() {
        @Override
        public void mostConfidentTxStateChanged(Transaction tx, State state, Transactions txList) {
            if (bitcoinInvoiceCallbackInterface != null) {
                bitcoinInvoiceCallbackInterface.onInvoiceStateChanged(TRANSFERSTATECHANGE, BitcoinInvoice.this, state, tx, txList);
            }
        }
    };


    private Vector<Coupon> coupons = new Vector<>();
    private Vector<String> keys = new Vector<>();

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

    /**
     * This method is yet needed to add the couponWallet to the peerGroup owned by the parent class
     *
     * @return couponWallet
     */
    Wallet getCouponWallet() { // TODO remove this method
        return couponWallet;
    }

    @Override
    public void onWalletChanged(Wallet wallet) { // couponWallet
        tryPayWithCoupons();
    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
        tryPayWithCoupons();
    }

    /**
     * Tries to pay the invoice using coupons.
     *
     * @return null or signed transaction ready for broadcasting
     */
    Transaction tryPayWithCoupons() {
        if (incomingTxList.isOneOrMoreTxPending()) {
            if (!incomingTxList.isOneOrMoreTxConfirmed())
                logger.info(String.format("Invoice %s has already been paid.", invoiceId.toString()));
            return null;
        }

        Coin balance = couponWallet.getBalance(new CouponCoinSelector());
        if (balance.getValue() < totalAmount) {
            logger.info(String.format("Invoice %s has too few coupons in wallet: %s",
                    invoiceId.toString(),
                    balance.toFriendlyString()));
            return null;
        }

        logger.info(String.format("Invoice %s will be paid using coupons.", invoiceId.toString()));
        Transaction tx = new Transaction(params);
        addTransfersToTx(tx);
        SendRequest sr = SendRequest.forTx(tx);
        sr.coinSelector = new CouponCoinSelector();
        // sr.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

        if (balance.getValue() > (totalAmount + 2 * Transaction.MIN_NONDUST_OUTPUT.getValue())) {
            sr.tx.addOutput(Coin.valueOf(totalAmount - transferAmount), transferAddress);
            sr.changeAddress = coupons.lastElement().ecKey.toAddress(params);
        } else {
            sr.changeAddress = transferAddress;
        }

        Transaction result = null;
        try {
            couponWallet.completeTx(sr); // TODO this is a race in case two invoices use the same (yet unfunded) coupon
            couponWallet.commitTx(sr.tx);
            result = sr.tx;
            System.out.println(HEX.encode(result.bitcoinSerialize())); // this serialized tx could be posted to block explorer, see https://github.com/IUNO-TDM/PaymentService/issues/49
            incomingTxList.add(sr.tx);
            if (!transfers.isEmpty()) transferTxList.add(sr.tx);
        } catch (InsufficientMoneyException e) { // should never happen
            e.printStackTrace();
        }

        return result;
    }

    private static String getUtxoString(String b58) throws IOException {
        URL url;
        StringBuilder response = new StringBuilder();
        url = new URL(blockexplorerAddr + "/addr/" + b58 + "/utxo");
        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        if (!blockexplorerPasswd.isEmpty() && !blockexplorerUser.isEmpty()) {
            String userpass = blockexplorerUser + ":" + blockexplorerPasswd;
            String basicAuth = "Basic " + Base64.getEncoder().encodeToString(userpass.getBytes());
            con.setRequestProperty("Authorization", basicAuth);
        }

        con.setRequestProperty("Content-Type", "application/json");
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        con.disconnect();
        return response.toString();
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
            final int confirmations = jsonObject.getInt("confirmations"); // confirmations
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
                tx.getConfidence().setDepthInBlocks(confirmations);
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

    static void importParams(HashMap<String, String> params) {
        if (params.containsKey(PARAM_KEY_BE_ADDR)) {
            blockexplorerAddr = params.get(PARAM_KEY_BE_ADDR);
        }
        if (params.containsKey(PARAM_KEY_BE_PASSWD)) {
            blockexplorerPasswd = params.get(PARAM_KEY_BE_PASSWD);
        }
        if (params.containsKey(PARAM_KEY_BE_USER)) {
            blockexplorerUser = params.get(PARAM_KEY_BE_USER);
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
    BitcoinInvoice(UUID id, Invoice inv, Address addr, Address addr2, BitcoinInvoiceStateChangedEventListener callbackInterface, DeterministicSeed seed) throws IllegalArgumentException {
        bitcoinInvoiceCallbackInterface = callbackInterface;
        incomingTxList.addStateListener(incomingTxStateListener);
        transferTxList.addStateListener(transferTxStateListener);
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
        receiveAddress = addr;
        transferAddress = addr2;

        Stopwatch watch = Stopwatch.createStarted();
        group = new KeyChainGroup(params, seed);
        group.setLookaheadSize(4);
        couponWallet = new Wallet(params, group);

        watch.stop();
        logger.info("creating wallet took {}", watch);

        couponWallet.addChangeEventListener(this); // FIXME add appropriate call to remove the listener
        couponWallet.addTransactionConfidenceEventListener(this); // FIXME add appropriate call to remove the listener
        useIncomingPaymentForTransfers = true;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            couponWallet.removeChangeEventListener(this); // FIXME invoice will never be cleaned up due to pending reference inside couponWallet
            couponWallet.removeTransactionConfidenceEventListener(this); // FIXME invoice will never be cleaned up due to pending reference inside couponWallet
            incomingTxList.removeStateListener(incomingTxStateListener);
            transferTxList.removeStateListener(transferTxStateListener);
        } finally {
            super.finalize();
        }
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
        for (TransferPair pa : transfers)
            result.addTransfersItem(pa.getAddressValuePair());
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
        // https://github.com/bitcoin/bips/blob/master/bip-0021.mediawiki defines
        // label: Label for that address (e.g. name of receiver)
        // message: message that describes the transaction to the user
        String label = String.format("IUNO %1$s (%2$s)", referenceId, invoiceId);
        String message = String.format("Reference %1$s", referenceId);
        return BitcoinURI.convertToBitcoinURI(receiveAddress, Coin.valueOf(totalAmount), label, message);
    }

    /**
     * Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.
     * The address value pair of the own wallet is added to the transfers as well.
     *
     * @return the address value/pairs to fulfill the invoice as array
     */
    List<AddressValuePair> getTransfers() {
        List<AddressValuePair> avpList = new Vector<>();
        for (TransferPair pa : transfers)
            avpList.add(pa.getAddressValuePair());

        long difference = totalAmount - transferAmount;
        avpList.add(new AddressValuePair().address(transferAddress.toBase58()).coin(difference));
        return avpList;
    }

    State getState() {
        return incomingTxList.getMostConfidentState();
    }

    State getTransferState() throws NoSuchFieldException {
        if (transfers.isEmpty()) {
            throw new NoSuchFieldException("TransactionState not applicable. No transfers for this invoice.");
        }
        return transferTxList.getMostConfidentState();
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
    SendRequest tryFinishInvoice(Wallet wallet) {
        if (transfers.isEmpty()) {
            logger.debug(String.format("Invoice %s has no transfers.", invoiceId.toString()));
            return null;
        }
        if (!incomingTxList.isOneOrMoreTxPending()) {
            logger.debug(String.format("Invoice %s has not yet been paid.", invoiceId.toString()));
            return null;
        }
        if (transferTxList.isOneOrMoreTxPending()) {
            logger.debug(String.format("Invoices %s transfers are already payed.", invoiceId.toString()));
            return null;
        }

        Transaction tx = new Transaction(params);

        if (useIncomingPaymentForTransfers)
            for (TransactionInput txin : getInputs())
                tx.addInput(txin);

        addTransfersToTx(tx);

        tx.setMemo(invoiceId.toString());
        SendRequest sr = SendRequest.forTx(tx);

        try {
            wallet.completeTx(sr);
            wallet.maybeCommitTx(sr.tx);
            transferTxList.add(sr.tx);
            logger.debug(String.format("Forwarded transfers for invoice %s.", invoiceId.toString()));
        } catch (InsufficientMoneyException e) {
            logger.debug(String.format("%s: too few coins in wallet to fulfill transfer payment", invoiceId.toString()));
            sr = null;
        }

        return sr;
    }

    private void addTransfersToTx(Transaction tx) {
        // add transfer outputs
        for (TransferPair pa : transfers) {
            tx.addOutput(pa.targetValue, pa.address);
        }
    }

    private boolean doesTxFulfillTransferPayment(HashMap<Address, Coin> acm) {
        // own wallet must be paid
        return (acm.keySet().contains(transferAddress)) // own wallet must be paid
                && (totalAmount - transferAmount) <= acm.get(transferAddress).getValue()
                && doesTxFulfillTransfers(acm);
    }

    private boolean doesTxFulfillTransfers(HashMap<Address, Coin> acm) {
        boolean result = true;
        for (TransferPair pa : transfers) { // all transfers must be paid as well
            if (!(acm.keySet().contains(pa.address))
                    || (pa.targetValue.isGreaterThan(acm.get(pa.address)))) {
                result = false;
                break;
            }
        }
        return result;
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     *
     * @param tx new transaction with outputs to be checked
     */
    boolean sortOutputsToAddresses(Transaction tx, HashMap<Address, Coin> addressCoinHashMap) {
        logger.debug("transaction script: " + HEX.encode(tx.bitcoinSerialize()));
        boolean ret = false;
        if (addressCoinHashMap.keySet().contains(receiveAddress)) {
            long value = addressCoinHashMap.get(receiveAddress).getValue();
            if (totalAmount <= value) { // transaction fulfills direct payment
                logger.info("Received direct payment for invoice " + invoiceId.toString()
                        + " to " + receiveAddress
                        + " with " + addressCoinHashMap.get(receiveAddress).toFriendlyString());
                incomingTxList.add(tx);
            }
            ret = true;

        } else if (doesTxFulfillTransferPayment(addressCoinHashMap)) {
            logger.info("Received transfer payment for invoice " + invoiceId.toString()
                    + " to " + transferAddress);
            incomingTxList.add(tx);
            if (!transfers.isEmpty()) transferTxList.add(tx);

        } else if (!transfers.isEmpty() && doesTxFulfillTransfers(addressCoinHashMap)) {
            // this is to recognize a transfer payment that became changed by malleability or a double spend
            logger.info(String.format("%s received transfers only", invoiceId.toString()));
            transferTxList.add(tx);

        } else {
            //silently do nothing since this will happen with transfer payments
        }

        return ret;
    }
}