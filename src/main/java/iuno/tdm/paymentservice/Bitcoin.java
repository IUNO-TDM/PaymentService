/**
 * Copyright 2016 TRUMPF Werkzeugmaschinen GmbH + Co. KG
 * Created by Hans-Peter Bock on 20.02.2017.
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

import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import io.swagger.model.AddressValuePair;
import io.swagger.model.Invoice;
import io.swagger.model.State;
import io.swagger.model.Transactions;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletChangeEventListener;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.constraints.Null;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class Bitcoin implements WalletCoinsReceivedEventListener, WalletChangeEventListener, BitcoinInvoiceCallbackInterface {
    final static int CLEANUPINTERVAL = 20; // clean up every n minutes

    private Wallet wallet = null;
    private PeerGroup peerGroup = null;
    private static final Logger logger = LoggerFactory.getLogger(Bitcoin.class);
    private DateTime lastCleanup = DateTime.now();
    private DeterministicSeed randomSeed;

    private HashMap<UUID, BitcoinInvoice> invoiceHashMap = new HashMap<>();
    private HashMap<Address, BitcoinInvoice> addressHashMap = new HashMap<>();
    private HashMap<Wallet, BitcoinInvoice> couponWalletBitcoinInvoiceHashMap = new HashMap<>();

    private static final String PREFIX = "PaymentService";


    private CopyOnWriteArrayList<BitcoinCallbackInterface> callbackClients = new CopyOnWriteArrayList<>();

    private static Bitcoin instance;
    private Context context;

    private HashMap<String, String> params = new HashMap<String, String>();

    private Bitcoin() {
        BriefLogFormatter.initWithSilentBitcoinJ();
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel("info"));

        // Context.enableStrictMode();
        final NetworkParameters params = TestNet3Params.get();
        context = new Context(params);
        Context.propagate(context);

        byte[] seed = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS/8];
        List<String> mnemonic = new ArrayList<>(0);
        randomSeed = new DeterministicSeed(seed, mnemonic, MnemonicCode.BIP39_STANDARDISATION_TIME_SECS);
    }

    public void addParams(HashMap<String, String> params){
        this.params.putAll(params);
        BitcoinInvoice.importParams(this.params);
    }

    public static synchronized Bitcoin getInstance() {
        if (Bitcoin.instance == null) {
            Bitcoin.instance = new Bitcoin();
        } else {
            Context.propagate(instance.context);
        }
        return Bitcoin.instance;
    }

    public void start() { // TODO: this method must be called once only!
        String workDir = System.getProperty("user.home") + "/." + PREFIX;
        new File(workDir).mkdirs();

        File chainFile = new File(workDir, PREFIX + ".spvchain");
        File walletFile = new File(workDir, PREFIX + ".wallet");
        File oldBackupFile = new File(System.getProperty("user.home"), PREFIX + ".wallet");
        File backupFile = new File(workDir, PREFIX + ".backup");

        // migrate from old to new backupfile; remove the oldbackup code in 2018
        if (!backupFile.exists() && oldBackupFile.exists()) {
            oldBackupFile.renameTo(backupFile);
        }

        // try to load regular wallet or if not existant load backup wallet or create new wallet
        // fail if an existing wallet file can not be read and admin needs to examine the wallets
        String filename = "n/a";
        try {
            if (walletFile.exists()) {
                filename = walletFile.toString();
                wallet = Wallet.loadFromFile(walletFile);

            } else {
                if (backupFile.exists()) {
                    filename = backupFile.toString();
                    wallet = Wallet.loadFromFile(backupFile);

                } else {
                    wallet = new Wallet(context);
                }
                chainFile.delete();
            }

        } catch (UnreadableWalletException e) {
            logger.warn(String.format("wallet file %s could not be read: %s", filename, e.getMessage()));
            e.printStackTrace();
            return;
        }

        // eventually create backup file
        try {
            if (!backupFile.exists()) wallet.saveToFile(backupFile);
        } catch (IOException e) {
            logger.error(String.format("creating backup wallet failed: %s", e.getMessage()));
            e.printStackTrace();
            return;
       }

        // wallets configuration
        if (!chainFile.exists())
            wallet.reset(); // reset wallet if chainfile does not exist
        // wallet.allowSpendingUnconfirmedTransactions();
        wallet.addCoinsReceivedEventListener(this);
        wallet.setDescription(this.getClass().getName());
        logStatus();

        // auto save wallets at least every five seconds
        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            logger.error(String.format("creating wallet file failed: %s", e.getMessage()));
            e.printStackTrace();
            return;
        }

        // initialize blockchain file
        BlockChain blockChain;
        try {
            blockChain = new BlockChain(context, wallet, new SPVBlockStore(context.getParams(), chainFile));
        } catch (BlockStoreException e) {
            e.printStackTrace();
            return;
        }

        // initialize peer group
        peerGroup = new PeerGroup(context, blockChain);
        peerGroup.addWallet(wallet);

        peerGroup.addPeerDiscovery(new DnsDiscovery(context.getParams()));
        Futures.addCallback(peerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object o) {
                        logger.info("peer group finished starting");
                        peerGroup.connectTo(new InetSocketAddress("tdm-payment.axoom.cloud", 18333)); // TODO make this configurable
                        peerGroup.startBlockChainDownload(new DownloadProgressTracker());
                    }

                    @Override
                    public void onFailure(Throwable throwable) {

                    }
                }
        );
    }

    public void stop() {
        peerGroup.stop();
        wallet.shutdownAutosaveAndWait();
    }

    private void logStatus() {
        logger.info("Balance: " + wallet.getBalance().toFriendlyString());
        logger.info("Estimated: " + wallet.getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString());
        logger.info("Seed: " + wallet.getKeyChainSeed().getMnemonicCode());
        logger.info("wallet receive address: " + wallet.currentReceiveAddress());
    }

    public boolean isRunning() {
        return ((null != peerGroup) && (0 < peerGroup.numConnectedPeers()));
    }

    public UUID addInvoice(Invoice inv) {
        UUID invoiceId = UUID.randomUUID();
        inv.invoiceId(invoiceId);
        BitcoinInvoice bcInvoice = new BitcoinInvoice(invoiceId, inv, wallet.freshReceiveAddress(), wallet.freshReceiveAddress(), this, randomSeed);
        Wallet couponWallet = bcInvoice.getCouponWallet();
        couponWallet.addChangeEventListener(this);
        peerGroup.addWallet(couponWallet);
        couponWalletBitcoinInvoiceHashMap.put(wallet, bcInvoice);

        // add invoice to hashmaps
        invoiceHashMap.put(invoiceId, bcInvoice);
        addressHashMap.put(bcInvoice.getReceiveAddress(), bcInvoice);
        addressHashMap.put(bcInvoice.getTransferAddress(), bcInvoice);
        logger.info("Added invoice " + invoiceId.toString() + " to hashmap.");
        logger.info(invoiceId.toString() + " - " + bcInvoice.getBip21URI());
        return invoiceId;
    }

    public AddressValuePair addCoupon(UUID id, String key) throws IOException {
        BitcoinInvoice invoice = invoiceHashMap.get(id);
        AddressValuePair avp = invoice.addCoupon(key);
        Transaction tx = invoice.tryPayWithCoupons();
        if (null != tx)
            syncBroadcastTransaction(tx);
        return avp;
    }

    public BitcoinInvoice getBitcoinInvoiceById(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id);
    }

    public String getInvoiceBip21(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).getBip21URI();
    }

    public List<AddressValuePair> getInvoiceTransfers(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).getTransfers();
    }

    public State getInvoiceState(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).getState();
    }

    public State getInvoiceTransferState(UUID id) throws NullPointerException, NoSuchFieldException {
        return invoiceHashMap.get(id).getTransferState();
    }

    public Transactions getInvoicePaymentTransactions(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).getPayingTransactions();
    }

    public Transactions getInvoiceTransferTransactions(UUID id) throws NullPointerException, NoSuchFieldException {
        return invoiceHashMap.get(id).getTransferTransactions();
    }


    public void deleteInvoiceById(UUID id) {
        BitcoinInvoice bcInvoice = invoiceHashMap.get(id);
        Wallet couponWallet = bcInvoice.getCouponWallet();
        couponWalletBitcoinInvoiceHashMap.remove(couponWallet);
        peerGroup.removeWallet(couponWallet);
        couponWallet.removeChangeEventListener(this);
        invoiceHashMap.remove(id);
        addressHashMap.remove(bcInvoice.getReceiveAddress());
        addressHashMap.remove(bcInvoice.getTransferAddress());
    }

    public Set<UUID> getInvoiceIds() {
        return invoiceHashMap.keySet();
    }

    /**
     * Removes all expired invoices.
     */
    private void cleanUpInvoices() {
        List<UUID> ids = new ArrayList();
        for (UUID id : invoiceHashMap.keySet()) { // get expired invoices...
            if (invoiceHashMap.get(id).isExpired()) {
                ids.add(id);
            }
        }
        for (UUID id : ids) { // ...and remove them all
            deleteInvoiceById(id);
            logger.info("Removed expired invoice with id " + id.toString());
        }
        lastCleanup = DateTime.now();
    }

    synchronized void syncBroadcastTransaction(Transaction tx) {
        peerGroup.broadcastTransaction(tx).broadcast();
    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        logger.info("Received tx with " + tx.getValueSentToMe(wallet).toFriendlyString() + ": " + tx);

        // create a hashmap with addresses and the sum of all coins send to each one
        HashMap<Address, Coin> addressCoinHashMap = new HashMap<>();
        for (TransactionOutput txout : tx.getOutputs()) {
            Address addr = txout.getAddressFromP2PKHScript(context.getParams());
            if(addr == null) addr = txout.getAddressFromP2SH(context.getParams());
            Coin value = txout.getValue();
            if (null != addr) {
                if (! addressCoinHashMap.containsKey(addr)) {
                    addressCoinHashMap.put(addr, value);

                } else {
                    addressCoinHashMap.put(addr, addressCoinHashMap.get(addr).add(value));
                }
            }
        }

        for (Address address : addressCoinHashMap.keySet()) {
            if (addressHashMap.containsKey(address)) {
                BitcoinInvoice bcInvoice = addressHashMap.get(address);
                bcInvoice.sortOutputsToAddresses(tx, addressCoinHashMap);

                SendRequest sr = bcInvoice.tryFinishInvoice();
                if (null != sr) {
                    try {
                        wallet.completeTx(sr);
                        wallet.maybeCommitTx(sr.tx);
                        syncBroadcastTransaction(sr.tx);
                    } catch (InsufficientMoneyException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // cleanup expired transactions
        if (lastCleanup.plusMinutes(CLEANUPINTERVAL).isBeforeNow()) {
            cleanUpInvoices();
        }
    }


    @Override
    public void onWalletChanged(Wallet wallet) {
        BitcoinInvoice bcInvoice = couponWalletBitcoinInvoiceHashMap.get(wallet);
        if (null != bcInvoice) {
            Transaction txCoupon = bcInvoice.tryPayWithCoupons();
            if (null != txCoupon) {
                // syncBroadcastTransaction(tx);
            }
        }
    }

    public void registerCallbackInterfaceClient(BitcoinCallbackInterface callbackClient){
        callbackClients.add(callbackClient);
    }

    public void unregisterCallbackInterfaceClient(BitcoinCallbackInterface callbackClient){
        callbackClients.remove(callbackClient);
    }

    public void sendInvoiceStateChangeToCallbackClients(Invoice invoice, State state){
        for (BitcoinCallbackInterface client:callbackClients) {
            client.invoiceStateChanged(invoice,state);
        }

    }
    public void sendInvoiceTransferStateChangeToCallbackClients(Invoice invoice, State state){
        for (BitcoinCallbackInterface client:callbackClients) {
            client.invoiceTransferStateChanged(invoice,state);
        }

    }

    public void sendPayingTransactionsChangedToCallbackClients(Invoice invoice, Transactions transactions){
        for (BitcoinCallbackInterface client:callbackClients) {
            client.invoicePayingTransactionsChanged(invoice, transactions);
        }
    }

    public void sendTransferTransactionsChangedToCallbackClients(Invoice invoice, Transactions transactions){
        for (BitcoinCallbackInterface client:callbackClients) {
            client.invoiceTransferTransactionsChanged(invoice, transactions);
        }
    }

    @Override
    public void invoiceStateChanged(BitcoinInvoice invoice, State state) {
        sendInvoiceStateChangeToCallbackClients(invoice.getInvoice(),state);
    }

    @Override
    public void invoiceTransferStateChanged(BitcoinInvoice invoice, State state) {
        sendInvoiceTransferStateChangeToCallbackClients(invoice.getInvoice(),state);
    }

    @Override
    public void invoicePayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        sendPayingTransactionsChangedToCallbackClients(invoice.getInvoice(),transactions);
    }

    @Override
    public void invoiceTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        sendTransferTransactionsChangedToCallbackClients(invoice.getInvoice(),transactions);
    }
}
