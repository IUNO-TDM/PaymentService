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

import io.swagger.model.Invoice;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Bitcoin implements WalletCoinsReceivedEventListener {
    final NetworkParameters params = TestNet3Params.get();
    final static int CLEANUPINTERVAL = 20; // clean up every n minutes

    private Wallet wallet = null;
    private PeerGroup peerGroup = null;
    private Logger logger;
    private DateTime lastCleanup = DateTime.now();

    private HashMap<UUID, BitcoinInvoice> invoiceHashMap = new HashMap<>();

    private static final String PREFIX = "PaymentService";

    private static Bitcoin instance;

    private Bitcoin() {
        logger = LoggerFactory.getLogger(Bitcoin.class);
        BriefLogFormatter.initWithSilentBitcoinJ();
    }

    public static synchronized Bitcoin getInstance() {
        if (Bitcoin.instance == null) {
            Bitcoin.instance = new Bitcoin();
        }
        return Bitcoin.instance;
    }

    public void start() { // TODO: this method must be called once only!
        String homeDir = System.getProperty("user.home");
        File chainFile = new File(homeDir, PREFIX + ".spvchain");
        File walletFile = new File(homeDir, PREFIX + ".wallet");

        // create new wallet system
        try {
            wallet = Wallet.loadFromFile(walletFile);
        } catch (UnreadableWalletException e) {
            logger.warn("creating new wallet");
            wallet = new Wallet(params);
        }

        // wallets configuration
        if (!chainFile.exists()) wallet.reset(); // reset wallet if chainfile does not exist
        // wallet.allowSpendingUnconfirmedTransactions();
        wallet.addCoinsReceivedEventListener(this);
        logStatus();

        // auto save wallets at least every five seconds
        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // initialize blockchain file
        BlockChain blockChain = null;
        try {
            blockChain = new BlockChain(params, wallet, new SPVBlockStore(params, chainFile));
        } catch (BlockStoreException e) {
            e.printStackTrace();
            return;
        }

        // initialize peer group
        peerGroup = new PeerGroup(params, blockChain);
        peerGroup.addWallet(wallet);

        peerGroup.startAsync();
        peerGroup.addPeerDiscovery(new DnsDiscovery(params));
        peerGroup.startBlockChainDownload(new DownloadProgressTracker());
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
        BitcoinInvoice bcInvoice = new BitcoinInvoice(invoiceId, inv, wallet, peerGroup);

        // add invoice to hashMap
        invoiceHashMap.put(invoiceId, bcInvoice);
        logger.info("Added invoice " + invoiceId.toString() + " to hashmap.");
        logger.info(invoiceId.toString() + ": " + bcInvoice.getBip21URI());
        return invoiceId;
    }

    public Invoice getInvoiceById(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).invoice;
    }

    public String getInvoiceBip21(UUID id) throws NullPointerException {
        return invoiceHashMap.get(id).getBip21URI();
    }

    public void deleteInvoiceById(UUID id) {
        invoiceHashMap.remove(id);
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

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        Coin value = tx.getValueSentToMe(wallet);
        logger.info("Received tx for " + value.toFriendlyString() + ": " + tx);
        for (BitcoinInvoice bcInvoice : invoiceHashMap.values()) {
            bcInvoice.checkTxForPayment(tx); // TODO this is inefficient
        }

        if (lastCleanup.plusMinutes(CLEANUPINTERVAL).isBeforeNow()) {
            cleanUpInvoices();
        }
    }
}
