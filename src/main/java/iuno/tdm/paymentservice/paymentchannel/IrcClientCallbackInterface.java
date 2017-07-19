package iuno.tdm.paymentservice.paymentchannel;

/**
 * Created by goergch on 19.07.17.
 */
public interface IrcClientCallbackInterface {
    void onMessage(String message);
}
