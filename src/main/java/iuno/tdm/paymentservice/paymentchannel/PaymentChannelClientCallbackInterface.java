package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;

/**
 * Created by goergch on 19.07.17.
 */
public interface PaymentChannelClientCallbackInterface {
    void channelOpen(PaymentChannelClient client);
    void receivedPayment(PaymentChannelClient client, Coin amount, String invoiceId);
    void channelClosed(PaymentChannelClient client);
}
