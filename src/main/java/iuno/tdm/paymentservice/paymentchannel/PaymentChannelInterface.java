package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;

import java.util.UUID;

public interface PaymentChannelInterface {
    void trySendPayment(String pubKey, Coin amount, String invoiceId);
    boolean isIrcConnected();
}
