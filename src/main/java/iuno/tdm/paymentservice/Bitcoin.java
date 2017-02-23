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

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Bitcoin {
    final NetworkParameters params = TestNet3Params.get();

    private Wallet wallet = null;
    private PeerGroup peerGroup = null;
    private Logger logger;

    private static final String PREFIX = "PaymentService";

    private static Bitcoin instance;

    private Bitcoin() {
        logger = LoggerFactory.getLogger(Bitcoin.class);
    }

    public static synchronized Bitcoin getInstance() {
        if (Bitcoin.instance == null) {
            Bitcoin.instance = new Bitcoin ();
        }
        return Bitcoin.instance;
    }

    public void start() {
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
        // wallet.allowSpendingUnconfirmedTransactions();
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
    }

    public void stop() {
        peerGroup.stop();
        wallet.shutdownAutosaveAndWait();
    }

    public void logStatus() {
        logger.info("Balance: " + wallet.getBalance().toFriendlyString());
        logger.info("Estimated: " + wallet.getBalance(Wallet.BalanceType.ESTIMATED).toFriendlyString());
        logger.info("Seed: " + wallet.getKeyChainSeed().getMnemonicCode());
        logger.info("wallet receive address: " + wallet.currentReceiveAddress());
    }

    public boolean isRunning() { return (null != peerGroup); }
}
