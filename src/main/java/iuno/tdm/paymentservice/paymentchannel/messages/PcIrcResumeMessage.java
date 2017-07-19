package iuno.tdm.paymentservice.paymentchannel.messages;


import iuno.tdm.paymentservice.paymentchannel.PcIrcMessage;
import iuno.tdm.paymentservice.paymentchannel.PcIrcMessageFactoryInterface;

/**
 * Created by goergch on 22.06.17.
 */
public class PcIrcResumeMessage implements PcIrcMessage {
    private String senderAddress;
    private String receiverAddress;
    private String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

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
            return new PcIrcResumeMessage(message);
        }
    };

    public PcIrcResumeMessage(String message) {
        receiverAddress = message.substring(0,10);
        senderAddress = message.substring(10,20);
        data = message.substring(24);
    }

    public PcIrcResumeMessage( String receiverAddress, String senderAddress, String data) {
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.data = data;
    }

    public static String getMessageIdentifier(){
        return "RESU";
    }

    public String getMessage() {
        String message = receiverAddress + senderAddress + getMessageIdentifier() +  data;
        return message;
    }
}
