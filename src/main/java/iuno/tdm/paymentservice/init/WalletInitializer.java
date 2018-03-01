package iuno.tdm.paymentservice.init;

import ch.qos.logback.classic.Level;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class WalletInitializer {

    private static final Logger logger = LoggerFactory.getLogger(WalletInitializer.class);

    public static void main(String[] args) {

        boolean generate = false;
        boolean seed = false;
        switch (args[0]) {
            case "seed":
                seed = true;
                break;
            case "generate":
                generate = true;
                break;
            default:
                System.out.println("Usage:\n seed: returns a new wallet's " +
                        "seed, creation time, and first receive address\n" +
                        "generate seed [creationtime] [passphrase]: generates and saves the wallet");
                break;
        }

        if (seed) {
            BriefLogFormatter.initWithSilentBitcoinJ();
            ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.OFF);
            final NetworkParameters params = TestNet3Params.get();
            Context context = new Context(params);
            Wallet wallet = new Wallet(context);
            DeterministicSeed generatedSeed = wallet.getKeyChainSeed();
            String seedString = generatedSeed.getMnemonicCode().toString();
            Address address = wallet.freshReceiveAddress();
            System.out.println(seedString.substring(1, seedString.length() - 1));
            System.out.println(generatedSeed.getCreationTimeSeconds());
            System.out.println(address.toBase58());
        } else if (generate) {
            BriefLogFormatter.initWithSilentBitcoinJ();
            ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                    LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(Level.INFO);
            final NetworkParameters params = TestNet3Params.get();
            Context context = new Context(params);
            String predefinedSeed = null;
            String passphrase = "";
            Calendar cal = Calendar.getInstance();
            cal.set(2008, 1, 1);
            long creationTime = cal.getTimeInMillis();
            if (args.length == 2) {

                predefinedSeed = args[1];
            }
            if (args.length == 3) {
                creationTime = Integer.parseInt(args[2]);
            }
            if (args.length == 4) {
                passphrase = args[3];
            }
            initializeWallet("PaymentService", context, true, predefinedSeed, passphrase, creationTime);
        }
    }


    public static Wallet initializeWallet(String prefix, Context context,
                                          boolean automaticallyRecoverBrokenWallet,
                                          String predefinedSeed,
                                          String passphrase,
                                          long creationtime) {
        Wallet wallet = null;

        String workDir = System.getProperty("user.home") + "/." + prefix;
        new File(workDir).mkdirs();

        File chainFile = new File(workDir, prefix + ".spvchain");
        File walletFile = new File(workDir, prefix + ".wallet");
        File oldBackupFile = new File(System.getProperty("user.home"), prefix + ".wallet");
        File backupFile = new File(workDir, prefix + ".backup");

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

            if (predefinedSeed.isEmpty()) {
                wallet = new Wallet(context); // create random new wallet
            } else {
                DeterministicSeed seed = tryCreateDeterministicSeed(predefinedSeed,
                        passphrase,
                        creationtime);
                if (seed == null) return wallet;
                wallet = Wallet.fromSeed(context.getParams(), seed);
            }
        }

        // 3 optionally try to load main wallet
        if ((null == wallet) && walletFile.exists()) {
            wallet = tryLoadWalletFromFile(walletFile);
            if ((null == wallet) && (!automaticallyRecoverBrokenWallet)) {
                logger.error("exiting because loading regular wallet file failed and automatic recovery is disabled");
                return wallet;
            }
        }

        // 4 optionally try to load backup wallet
        if ((null == wallet) && backupFile.exists()) {
            wallet = tryLoadWalletFromFile(backupFile);
        }

        // 5 optionally give up
        if (null == wallet) {
            logger.error("exiting because no wallet could be initialized, please check manually");
            return wallet;
        }

        // eventually create backup file


        // wallets configuration
        if (!chainFile.exists())
            wallet.reset(); // reset wallet if chainfile does not exist
        // wallet.allowSpendingUnconfirmedTransactions();

        // auto save wallets at least every five seconds
        try {
            wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null).saveNow();
        } catch (IOException e) {
            logger.error(String.format("creating wallet file failed: %s", e.getMessage()));
            e.printStackTrace();
            return wallet;
        }

        return wallet;
    }


    private static DeterministicSeed tryCreateDeterministicSeed(String seedCode, String passphrase, long creationtime) {
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
    private static Wallet tryLoadWalletFromFile(File walletFile) {
        Wallet w = null;
        try {
            w = Wallet.loadFromFile(walletFile);
        } catch (UnreadableWalletException e) {
            logger.warn(String.format("wallet file %s could not be read: %s", walletFile.toString(), e.getMessage()));
            e.printStackTrace();
        }
        return w;
    }
}
