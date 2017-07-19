package iuno.tdm.paymentservice.paymentchannel.messages;

import iuno.tdm.paymentservice.paymentchannel.PcIrcMessage;
import iuno.tdm.paymentservice.paymentchannel.PcIrcMessageFactoryInterface;

/**
 * Created by goergch on 21.06.17.
 */
public class PcIrcConnectMessage implements PcIrcMessage {

    private String senderAddress;
    private String receiverAddress;
    private String challenge;
    private String senderPubKey;
    private String receiverPubKey;

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
    }

    public String getChallenge() {
        return challenge;
    }

    public void setChallenge(String challenge) {
        this.challenge = challenge;
    }

    public String getSenderPubKey() {
        return senderPubKey;
    }

    public void setSenderPubKey(String senderPubKey) {
        this.senderPubKey = senderPubKey;
    }

    public String getReceiverPubKey() {
        return receiverPubKey;
    }

    public void setReceiverPubKey(String receiverPubKey) {
        this.receiverPubKey = receiverPubKey;
    }


    public static final PcIrcMessageFactoryInterface FACTORY = new PcIrcMessageFactoryInterface() {
        public PcIrcMessage create(String message) {
            return new PcIrcConnectMessage(message);
        }
    };

    public PcIrcConnectMessage(String message) {
        receiverAddress = message.substring(0,10);
        senderAddress = message.substring(10,20);
        receiverPubKey = message.substring(24,90);
        senderPubKey = message.substring(90,156);
        challenge = message.substring(156);
    }

    public PcIrcConnectMessage(String receiverAddress, String senderAddress, String receiverPubKey, String senderPubKey, String challenge) {
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.challenge = challenge;
        this.senderPubKey = senderPubKey;
        this.receiverPubKey = receiverPubKey;
    }

    public static String getMessageIdentifier(){
        return "CONN";
    }


    public String getMessage() {
        String message = receiverAddress + senderAddress + getMessageIdentifier() + receiverPubKey +  senderPubKey + challenge;
        return message;
    }
}
