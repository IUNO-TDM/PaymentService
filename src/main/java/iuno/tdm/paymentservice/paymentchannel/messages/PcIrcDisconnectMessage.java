package iuno.tdm.paymentservice.paymentchannel.messages;

import iuno.tdm.paymentservice.paymentchannel.PcIrcMessage;
import iuno.tdm.paymentservice.paymentchannel.PcIrcMessageFactoryInterface;

/**
 * Created by goergch on 22.06.17.
 */
public class PcIrcDisconnectMessage implements PcIrcMessage {
    private String senderAddress;
    private String receiverAddress;

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


    public static final PcIrcMessageFactoryInterface FACTORY = new PcIrcMessageFactoryInterface() {
        public PcIrcMessage create(String message) {
            return new PcIrcDisconnectMessage(message);
        }
    };

    public PcIrcDisconnectMessage(String message) {
        receiverAddress = message.substring(0,10);
        senderAddress = message.substring(10,20);
    }

    public PcIrcDisconnectMessage( String receiverAddress, String senderAddress) {
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
    }

    public static String getMessageIdentifier(){
        return "DISC";
    }

    public String getMessage() {
        String message  = receiverAddress + senderAddress + getMessageIdentifier();
        return message;
    }
}
