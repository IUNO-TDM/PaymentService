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

import io.swagger.model.AddressValuePair;
import io.swagger.model.Invoice;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class Bitcoin implements WalletCoinsReceivedEventListener {
    final NetworkParameters params = TestNet3Params.get();

    private Wallet wallet = null;
    private PeerGroup peerGroup = null;
    private Logger logger;

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

    private class BitcoinInvoice {
        private UUID invoiceId;
        private Invoice invoice;
        private Coin totalAmount = Coin.ZERO;
        private Date expiration;
        private TransactionInput txin;
        private Address payto; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
        BitcoinInvoice(UUID id, Invoice inv) throws IllegalArgumentException {
            // check sanity of invoice
            totalAmount = Coin.valueOf(inv.getTotalAmount());
            if (totalAmount.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
                throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

            // check values (transfer shall be lower than totalamount)
            Coin transferAmount = Coin.ZERO;
            for (AddressValuePair avp : inv.getTransfers()) {
                Coin value = Coin.valueOf(avp.getCoin());
                if (value.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
                    throw new IllegalArgumentException("transfer amount to " + avp.getAddress() + " is less than bitcoin minimum dust output");
                transferAmount = transferAmount.add(value);
            }
            if (totalAmount.isLessThan(transferAmount))
                throw new IllegalArgumentException("total invoice amount is less than sum of transfer amounts");

            // expiration date shall be in the future
            expiration = inv.getExpiration();
            if (isExpired())
                throw new IllegalArgumentException("expiration date must be in the future");

            invoiceId = id;
            invoice = inv;
            payto = wallet.freshReceiveAddress(); // TODO unused addresses shall be recycled
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
        public String getBip21URI() {
            return BitcoinURI.convertToBitcoinURI(payto, totalAmount, "PaymentService", "all your coins belong to us");
        }

        private void payTransfers() {
            Transaction tx = new Transaction(params);
            tx.addInput(txin);
            for (AddressValuePair fwd : invoice.getTransfers()) {
                Coin value = Coin.valueOf(fwd.getCoin());
                Address address = Address.fromBase58(params, fwd.getAddress());
                logger.info("forward " + value.toFriendlyString() + " to " + address.toBase58());
                tx.addOutput(value, address);
            }
            SendRequest sr = SendRequest.forTx(tx);
            try {
                wallet.completeTx(sr);
                wallet.commitTx(sr.tx);
                peerGroup.broadcastTransaction(sr.tx).broadcast();
            } catch (InsufficientMoneyException e) {
                e.printStackTrace();
            }
        }

        /**
         * Checks all outputs of a transaction for payments to this invoice.
         * @deprecated this is an efficient way that only works for verifying just a few payments per second
         * @param tx new transaction with outputs to be checked
         */
        public void checkTxForPayment(Transaction tx) {
            for (TransactionOutput tout : tx.getOutputs()) {
                Address dest = tout.getAddressFromP2PKHScript(params);
                if ((payto.equals(dest))
                    && (totalAmount.getValue() <= tout.getValue().getValue())) {
                    logger.info("Received payment for invoice " + invoiceId.toString()
                            + " to " + tout.getAddressFromP2PKHScript(params)
                            + " with " + tout.getValue().toFriendlyString());
                    int index = tout.getIndex();
                    TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, tx);
                    byte[] script = tout.getScriptBytes();
                    txin = new TransactionInput(params, tx, script, txOutpoint);
                    txin.clearScriptBytes();
                    payTransfers();
                }
            }
        }
    }

    public UUID addInvoice(Invoice inv) {
        UUID invoiceId = UUID.randomUUID();
        BitcoinInvoice bcInvoice = new BitcoinInvoice(invoiceId, inv);

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

    /**
     * Removes all expired invoices.
     */
    private void cleanUpInvoices() {
        for (UUID id : invoiceHashMap.keySet()) {
            if (invoiceHashMap.get(id).isExpired()) {
                invoiceHashMap.remove(id);
            }
        }
    }

    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
        Coin value = tx.getValueSentToMe(wallet);
        logger.info("Received tx for " + value.toFriendlyString() + ": " + tx);
        for (BitcoinInvoice bcInvoice : invoiceHashMap.values()) {
            bcInvoice.checkTxForPayment(tx); // TODO this is inefficient
        }
    }
}
