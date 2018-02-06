package iuno.tdm.paymentservice.init;

import ch.qos.logback.classic.Level;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.LoggerFactory;

public class WalletInitializer {
    public static void main(String[] args) {
        BriefLogFormatter.initWithSilentBitcoinJ();
        ch.qos.logback.classic.Logger rootLogger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        rootLogger.setLevel(Level.OFF);
        boolean generate = false;
        switch (args[0]) {
            case "generate":
                generate = true;
                break;
            default:
                System.out.println("Usage:\n generate: returns a new wallet " +
                        "seed and creation time");
                break;
        }


        if(generate){
            final NetworkParameters params = TestNet3Params.get();
            Context context = new Context(params);
            Wallet wallet = new Wallet(context);
            DeterministicSeed seed = wallet.getKeyChainSeed();
            String seedString = seed.getMnemonicCode().toString();
            Address address = wallet.freshReceiveAddress();
            System.out.println(seedString.substring(1,seedString.length()-1));
            System.out.println(seed.getCreationTimeSeconds());
            System.out.println(address.toBase58());

        }



    }
}
