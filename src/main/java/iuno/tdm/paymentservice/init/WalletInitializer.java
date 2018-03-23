package iuno.tdm.paymentservice.init;

import ch.qos.logback.classic.Level;
import iuno.tdm.paymentservice.Bitcoin;
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
import java.util.Calendar;

public class WalletInitializer {

    private static final Logger logger = LoggerFactory.getLogger(WalletInitializer.class);

    public static void main(String[] args) throws Exception {

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
            String predefinedSeed = "";
            String passphrase = "";
            Calendar cal = Calendar.getInstance();
            cal.set(2008, 1, 1);
            long creationTime = cal.getTimeInMillis();
            if (args.length >= 2) {

                predefinedSeed = args[1];
            }
            if (args.length >= 3) {
                creationTime = Integer.parseInt(args[2]);
            }
            if (args.length >= 4) {
                passphrase = args[3];
            }
            initializeWallet(Bitcoin.PREFIX, context, predefinedSeed, passphrase, creationTime);
        }
    }


    private static void initializeWallet(String prefix, Context context,
                                          String predefinedSeed,
                                          String passphrase,
                                          long creationtime) throws Exception {
        String workDir = System.getProperty("user.home") + "/." + prefix;
        new File(workDir).mkdirs();

        File walletFile = new File(workDir, prefix + ".wallet");

        if (walletFile.exists()) throw new Exception("wallet file exists already");

        Wallet wallet;
        if (predefinedSeed.isEmpty()) {
            wallet = new Wallet(context); // create random new wallet
        } else {
            DeterministicSeed seed = tryCreateDeterministicSeed(predefinedSeed,
                    passphrase,
                    creationtime);
            if (seed == null) throw new Exception("seed is broken");
            wallet = Wallet.fromSeed(context.getParams(), seed);
        }
        wallet.saveToFile(walletFile);
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
}
