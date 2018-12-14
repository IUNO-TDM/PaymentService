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
import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Bitcoin implements WalletCoinsReceivedEventListener, WalletChangeEventListener {
    public static final String PREFIX = "PaymentService";

    private final static int CLEANUPINTERVAL = 20; // clean up every n minutes

    private final Coin minimumCash = Coin.valueOf(Long.parseLong(System.getProperty("minimumCash", "500000")));
    private final String savingsAddressString = System.getProperty("savingsAddress", "mgSgVGAaz7H99vLB4gAqH3f7Qr1jpcLoy2");
    private Address savingsAddress;

    private Wallet wallet = null;
    private PeerGroup peerGroup = null;
    private static final Logger logger = LoggerFactory.getLogger(Bitcoin.class);
    private DateTime lastCleanup = DateTime.now();
    private DeterministicSeed couponRandomSeed;

    private HashMap<UUID, BitcoinInvoice> invoiceHashMap = new HashMap<>();

    private static boolean automaticallyRecoverBrokenWallet;

    private static Bitcoin instance;
    private Context context;

    private ServletContext servletContext;
    private PaymentSocketIOServlet paymentSocketIOServlet;

    private HashMap<String, String> params = new HashMap<String, String>();

    private Bitcoin() {
        BriefLogFormatter.initWithSilentBitcoinJ();
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.toLevel("info"));

        // Context.enableStrictMode();
        final NetworkParameters params = TestNet3Params.get();
        context = new Context(params);
        Context.propagate(context);

        savingsAddress = Address.fromBase58(context.getParams(), savingsAddressString);

        // read system property to check if broken wallet shall be recovered from backup automatically
        automaticallyRecoverBrokenWallet = System.getProperty("automaticallyRecoverBrokenWallet", "false").equalsIgnoreCase("true");

        // prepare (unused) random seed to save time when constructing coupon wallets for invoices
        byte[] seed = new byte[DeterministicSeed.DEFAULT_SEED_ENTROPY_BITS/8];
        List<String> mnemonic = new ArrayList<>(0);
        couponRandomSeed = new DeterministicSeed(seed, mnemonic, MnemonicCode.BIP39_STANDARDISATION_TIME_SECS);
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

    public void start(ServletContext sctx) { // TODO: this method must be called once only!
        String workDir = System.getProperty("user.home") + "/." + PREFIX;
        if (!new File(workDir).mkdirs())
            logger.error("Could not create working directory " + workDir);

        servletContext = sctx;

        ServletContext socketioctx = servletContext.getContext("iuno.tdm.paymentservice.PaymentSocketIOServlet");

        paymentSocketIOServlet = (PaymentSocketIOServlet) socketioctx.getAttribute(PaymentSocketIOServlet.PAYMENTSERVLET);

        File chainFile = new File(workDir, PREFIX + ".spvchain");
        File walletFile = new File(workDir, PREFIX + ".wallet");
        File oldBackupFile = new File(System.getProperty("user.home"), PREFIX + ".wallet");
        File backupFile = new File(workDir, PREFIX + ".backup");

        // migrate from old to new backupfile; remove the oldbackup code in 2018
        if (!backupFile.exists() && oldBackupFile.exists()) {
            oldBackupFile.renameTo(backupFile);
        }

        // try to load regular wallet or if not existant load backup wallet or create new wallet
        // optionally fail if an existing wallet file can not be read and admin needs to examine the wallets
        // depending on configuration of automaticallyRecoverBrokenWallet

        // 1 remove chainfile if there is no regular wallet to replay blockchain
        if (!walletFile.exists()) chainFile.delete();

        // 2 if there is neither a wallet nor a backup create a new one
        if (!walletFile.exists() && !backupFile.exists()) {
            String seedCode = System.getProperty("walletSeed", "");
            if (seedCode.isEmpty()) {
                wallet = new Wallet(context); // create random new wallet
            } else {
                DeterministicSeed seed = tryCreateDeterministicSeed(seedCode,
                        System.getProperty("walletPassphrase", ""),
                        Long.parseLong(System.getProperty("walletCreationTime", "1504199300")));
                if (seed == null) return;
                wallet = Wallet.fromSeed(context.getParams(), seed);
            }
        }

        // 3 optionally try to load main wallet
        if ((null == wallet) && walletFile.exists()) {
            wallet = tryLoadWalletFromFile(walletFile);
            if ((null == wallet) && (!automaticallyRecoverBrokenWallet)) {
                logger.error("exiting because loading regular wallet file failed and automatic recovery is disabled");
                return;
            }
        }

        // 4 optionally try to load backup wallet
        if ((null == wallet) && backupFile.exists()) {
            wallet = tryLoadWalletFromFile(backupFile);
        }

        // 5 optionally give up
        if (null == wallet) {
            logger.error("exiting because no wallet could be initialized, please check manually");
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
        wallet.addChangeEventListener(this);
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

    private DeterministicSeed tryCreateDeterministicSeed(String seedCode, String passphrase, long creationtime) {
        DeterministicSeed seed = null;
        try {
            seed = new DeterministicSeed(seedCode.replaceAll(",", " "), null, passphrase, creationtime);
        } catch (UnreadableWalletException e) {
            e.printStackTrace();
            logger.error("could not create deterministic seed from given seed; " + e.getMessage());
        }
        return seed;
    }

    /***
     * This method tries to load a wallet from a file.
     * @param walletFile
     * @return null if loading failed, Wallet if it succeeded
     */
    private Wallet tryLoadWalletFromFile(File walletFile) {
        Wallet w = null;
        try {
            w = Wallet.loadFromFile(walletFile);
        } catch (UnreadableWalletException e) {
            logger.warn(String.format("wallet file %s could not be read: %s", walletFile.toString(), e.getMessage()));
            e.printStackTrace();
        }
        return w;
    }

    /**
     * This method sends excessive money to a safe wallet.
     */
    public static List<Transaction> createSafeMoneyTransactions(Wallet w, Address addr, Coin minValue, Coin maxValue) {
        assert(minValue.isLessThan(maxValue));

        List<Transaction> txs = new ArrayList<>();

        int count=1;
        Coin saved = Coin.ZERO;
        while (w.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE).isGreaterThan(maxValue)) {
            Coin amount = w.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE)
                    .subtract(minValue)
                    .div(count);

            SendRequest sr = SendRequest.to(addr, amount);
            // sr.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;

            try
            {
                w.completeTx(sr);
                w.commitTx(sr.tx);
                txs.add(sr.tx);
                saved.add(amount);
                count = 1;
            } catch (Wallet.ExceededMaxTransactionSize e) {
                count += 1;
                continue;
            } catch (InsufficientMoneyException m) {
                break;
            }
        }
        if (0 < txs.size())
            logger.info(String.format("Sent %d Satoshis to savings address using %d transactions.", saved.getValue(), txs.size()));
        return txs;
    }

    public void stop() {
        safeMoney(wallet);
        peerGroup.stop();
        wallet.removeChangeEventListener(this);
        wallet.removeCoinsReceivedEventListener(this);
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
        BitcoinInvoice bcInvoice = new BitcoinInvoice(invoiceId, inv, wallet.freshReceiveAddress(),
                wallet.freshReceiveAddress(), paymentSocketIOServlet, couponRandomSeed);
        bcInvoice.setUseIncomingPaymentForTransfers(Boolean.parseBoolean(System.getProperty("useIncomingPaymentForTransfers", "false")));

        peerGroup.addWallet(bcInvoice.getCouponWallet());

        // add invoice to hashmaps
        invoiceHashMap.put(invoiceId, bcInvoice);
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
        BitcoinInvoice bcInvoice = invoiceHashMap.get(id);
//        simpleDoubleSpend(bcInvoice.getReceiveAddress(), Coin.valueOf(bcInvoice.getInvoice().getTotalAmount()));
        return bcInvoice.getBip21URI();
    }

    private void simpleDoubleSpend(Address theirs, Coin amount) {
        Address mine = Address.fromBase58(context.getParams(), "n46V8bGmUpYpDQQhUZpwYmtwh1iMzxQ4XS");
        SendRequest first = SendRequest.to(theirs, amount);
        first.feePerKb = Transaction.REFERENCE_DEFAULT_MIN_TX_FEE;
        SendRequest second = SendRequest.to(mine, amount);
        try
        {
            wallet.completeTx(first);
            wallet.completeTx(second);
            int i=0;
            for (Peer p : peerGroup.getConnectedPeers()) {
                if (0 == i%2) p.sendMessage(first.tx);
                else p.sendMessage(second.tx);
                i++;
            }
            wallet.commitTx(first.tx);
            wallet.commitTx(second.tx);
        } catch (InsufficientMoneyException ignored) {
        }
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

    public long getEstimatedBalance(){
        return wallet.getBalance(Wallet.BalanceType.ESTIMATED).value;
    }

    public long getSpendableBalance(){
        return wallet.getBalance().value;
    }


    public void deleteInvoiceById(UUID id) {
        BitcoinInvoice bcInvoice = invoiceHashMap.get(id);
        Wallet couponWallet = bcInvoice.getCouponWallet();
        peerGroup.removeWallet(couponWallet);
        invoiceHashMap.remove(id);
    }

    public Set<UUID> getInvoiceIds() {
        return invoiceHashMap.keySet();
    }

    /**
     * Removes all expired invoices.
     */
    private void cleanUpInvoices() {
        List<UUID> ids = new ArrayList<>();
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

    /**
     * This method takes a list of transaction outputs and returns a hashmap that maps addresses to the sum ot the
     * received coin values.
     * @param txos list of transaction outputs
     * @return hashmap that maps coin values to addresses
     */
    private HashMap<Address, Coin> mapCoinsToAddresses(List<TransactionOutput> txos) {
        HashMap<Address, Coin> acm = new HashMap<>();
        for (TransactionOutput txout : txos) {
            Address addr = txout.getAddressFromP2PKHScript(context.getParams());
            if (null == addr)
                addr = txout.getAddressFromP2SH(context.getParams());

            if (null == addr)
                continue; // unknown address format, discard transaction output

            Coin value = txout.getValue();
            if (!acm.containsKey(addr))
                acm.put(addr, value);
            else
                acm.put(addr, acm.get(addr).add(value));
        }
        return acm;
    }

    @Override
    public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
        logger.info("Received tx with " + tx.getValueSentToMe(w).toFriendlyString() + ": " + tx);

        HashMap<Address, Coin> addressCoinHashMap = mapCoinsToAddresses(tx.getOutputs());

        boolean payTransfers;
        for (BitcoinInvoice bcInvoice : invoiceHashMap.values()) {
            payTransfers = bcInvoice.sortOutputsToAddresses(tx, addressCoinHashMap);
            if (payTransfers) {
                SendRequest sr = bcInvoice.tryFinishInvoice(w);
                if (null != sr)
                    syncBroadcastTransaction(sr.tx);
            }
        }

        logWalletBalance(w);
    }

    private void logWalletBalance(Wallet w) {
        logger.info(String.format("Balance: %s (%s est.)",
                w.getBalance(Wallet.BalanceType.AVAILABLE_SPENDABLE).toFriendlyString(),
                w.getBalance(Wallet.BalanceType.ESTIMATED_SPENDABLE).toFriendlyString()
        ));
    }

    private int blockHeight = 0;
    @Override
    public void onWalletChanged(Wallet w) {
        int newBlockHeight = w.getLastBlockSeenHeight();
        if (blockHeight < newBlockHeight) {
            tryFinishAllInvoices();
            safeMoney(w);
            logWalletBalance(w);
        }
        blockHeight = newBlockHeight; // assign, in case blocks have been reordered

        // cleanup expired transactions
        if (lastCleanup.plusMinutes(CLEANUPINTERVAL).isBeforeNow()) {
            cleanUpInvoices();
        }

        // check and limit number of connected peers
        // this workaround disconnects half of the excessive connected peers
        int numberOfPeersToDisconnect = (peerGroup.numConnectedPeers() - peerGroup.getMaxConnections()) / 2;
        if (0 < numberOfPeersToDisconnect)
            logger.info("disconnecting " + numberOfPeersToDisconnect + " peers");
        List<Peer> peers = peerGroup.getConnectedPeers();
        while (0 < numberOfPeersToDisconnect) {
            numberOfPeersToDisconnect--;
            peers.get(numberOfPeersToDisconnect).close();
        }
    }

    private void safeMoney(Wallet w) {
        List<Transaction> txs = createSafeMoneyTransactions(w, savingsAddress, minimumCash, minimumCash.multiply(2));
        for (Transaction tx : txs)
            syncBroadcastTransaction(tx);
    }

    private void tryFinishAllInvoices() {
        for (BitcoinInvoice bcInvoice : invoiceHashMap.values()) {
            SendRequest sr = bcInvoice.tryFinishInvoice(wallet);
            if (null != sr)
                syncBroadcastTransaction(sr.tx);
        }
    }
}
