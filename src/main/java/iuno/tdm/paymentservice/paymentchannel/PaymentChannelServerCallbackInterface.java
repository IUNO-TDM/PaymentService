package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;

/**
 * Created by goergch on 19.07.17.
 */
public interface PaymentChannelServerCallbackInterface {
    void channelOpen(PaymentChannelServer server);
    void receivedPayment(PaymentChannelServer server, Coin amount, String invoiceId);
    void channelClosed(PaymentChannelServer server);
}
