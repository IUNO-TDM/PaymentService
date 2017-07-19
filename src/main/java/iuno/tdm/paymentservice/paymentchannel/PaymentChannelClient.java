package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.Wallet;

import java.util.UUID;

/**
 * Created by goergch on 18.07.17.
 */
public class PaymentChannelClient implements IrcClientCallbackInterface{

    DeterministicKey myKey;
    String ownPubkey;
    String foreignPubkey;
    Coin initialChannelSize;
    Wallet wallet;
    PaymentChannelClientCallbackInterface callbackInterface;

    public PaymentChannelClient(DeterministicKey myKey, String ownPubkey, String foreignPubkey, Coin initialChannelSize, Wallet wallet, PaymentChannelClientCallbackInterface callbackInterface) {
        this.myKey = myKey;
        this.ownPubkey = ownPubkey;
        this.foreignPubkey = foreignPubkey;
        this.initialChannelSize = initialChannelSize;
        this.wallet = wallet;
        this.callbackInterface = callbackInterface;
        this.myKey = wallet.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
    }

    @Override
    public void onMessage(String message) {

    }


    public void sendPayment(String pubKey, Coin amount, UUID invoiceId, String pcInvoiceId) {

    }


    public int getChannelBalance(String pubKey) {
        return 0;
    }
}
