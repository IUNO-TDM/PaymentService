package iuno.tdm.paymentservice.paymentchannel.messages;


import iuno.tdm.paymentservice.paymentchannel.PcIrcMessage;
import iuno.tdm.paymentservice.paymentchannel.PcIrcMessageFactoryInterface;

/**
 * Created by goergch on 22.06.17.
 */
public class PcIrcDataMessage implements PcIrcMessage {
    private String data;
    private String senderAddress;
    private String receiverAddress;
    private int length;


    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
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
            return new PcIrcDataMessage(message);
        }
    };

    public PcIrcDataMessage(String message) {
        receiverAddress = message.substring(0,10);
        senderAddress = message.substring(10,20);
        length = Integer.parseInt(message.substring(24,28));
        data = message.substring(28);
    }

    public PcIrcDataMessage(String receiverAddress, String senderAddress, int length, String data) {
        this.data = data;
        this.senderAddress = senderAddress;
        this.receiverAddress = receiverAddress;
        this.length = length;
    }

    public static String getMessageIdentifier(){
        return "DATA";
    }

    public String getMessage() {
        String message = receiverAddress + senderAddress + getMessageIdentifier() + String.format("%04d", length) + data;
        return message;
    }
}
