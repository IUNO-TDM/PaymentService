package iuno.tdm.paymentservice.paymentchannel.messages;


import iuno.tdm.paymentservice.paymentchannel.PcIrcMessage;
import iuno.tdm.paymentservice.paymentchannel.PcIrcMessageFactoryInterface;

/**
 * Created by goergch on 22.06.17.
 */
public class PcIrcConnectedMessage implements PcIrcMessage {
    private String senderAddress;
    private String receiverAddress;
    private String signature;

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

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }


    public static final PcIrcMessageFactoryInterface FACTORY = new PcIrcMessageFactoryInterface() {
        public PcIrcMessage create(String message) {
            return new PcIrcConnectedMessage(message);
        }
    };

    public PcIrcConnectedMessage(String message) {
        receiverAddress = message.substring(0,10);
        senderAddress = message.substring(10,20);
        signature = message.substring(24);
    }

    public PcIrcConnectedMessage(String receiverAddress, String senderAddress, String signature){
        this.receiverAddress = receiverAddress;
        this.senderAddress = senderAddress;
        this.signature = signature;
    }


    public static String getMessageIdentifier(){
        return "COED";
    }

    public String getMessage() {
        String message = receiverAddress + senderAddress + getMessageIdentifier() + signature;
        return message;
    }
}
