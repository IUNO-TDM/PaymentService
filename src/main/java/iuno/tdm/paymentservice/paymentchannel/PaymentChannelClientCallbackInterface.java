package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;

/**
 * Created by goergch on 19.07.17.
 */
public interface PaymentChannelClientCallbackInterface {
    void channelOpen(PaymentChannelClient client);
    void channelClosed(PaymentChannelClient client);
}
