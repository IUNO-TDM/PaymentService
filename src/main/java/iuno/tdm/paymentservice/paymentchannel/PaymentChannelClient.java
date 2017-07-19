package iuno.tdm.paymentservice.paymentchannel;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.lambdaworks.codec.Base64;
import com.subgraph.orchid.encoders.Hex;
import iuno.tdm.paymentservice.paymentchannel.messages.PcIrcConnectedMessage;
import iuno.tdm.paymentservice.paymentchannel.messages.PcIrcDataMessage;
import iuno.tdm.paymentservice.paymentchannel.messages.PcIrcDisconnectMessage;
import iuno.tdm.paymentservice.paymentchannel.messages.PcIrcResumeMessage;
import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.protocols.channels.*;
import org.bitcoinj.wallet.KeyChain;
import org.bitcoinj.wallet.Wallet;

import java.security.SignatureException;

/**
 * Created by goergch on 18.07.17.
 */
public class PaymentChannelClient implements IrcClientCallbackInterface{

    private final String serverAddress;
    private final String clientAddress;
    private final ECKey foreignKey;
    private IrcClientInterface ircClient;
    private DeterministicKey myKey;
    private String ownPubkey;
    private String foreignPubkey;
    private Coin initialChannelSize;
    private Wallet wallet;
    private PaymentChannelClientCallbackInterface callbackInterface;
    private org.bitcoinj.protocols.channels.PaymentChannelClient channelClient;
    private final PcIrcMessageRegistry messageRegistry;
    private int dataToBeReceived;
    private StringBuilder builder;
    private String challenge;


    public String getOwnPubkey() {
        return ownPubkey;
    }

    public String getForeignPubkey() {
        return foreignPubkey;
    }

    public PaymentChannelClient(String foreignPubkey, final Coin initialChannelSize, Wallet wallet, final PaymentChannelClientCallbackInterface callbackInterface, final IrcClientInterface ircClient) {
        this.ircClient = ircClient;
        this.foreignPubkey = foreignPubkey;
        this.initialChannelSize = initialChannelSize;
        this.wallet = wallet;
        this.callbackInterface = callbackInterface;
        this.myKey = wallet.freshKey(KeyChain.KeyPurpose.RECEIVE_FUNDS);
        this.ownPubkey = myKey.getPublicKeyAsHex();

        this.messageRegistry = PcIrcMessageRegistry.createStandardRegistry();
        this.serverAddress = foreignPubkey.substring(0,10);
        this.clientAddress = myKey.getPublicKeyAsHex().substring(0,10);
        this.foreignKey = ECKey.fromPublicOnly(Hex.decode(foreignPubkey));

        channelClient = new org.bitcoinj.protocols.channels.PaymentChannelClient(wallet, myKey, initialChannelSize, getChannelHash(), new IPaymentChannelClient.ClientConnection() {
            @Override
            public void sendToServer(Protos.TwoWayChannelMessage msg) {
                String message =  String.copyValueOf(Base64.encode(msg.toByteArray()));
                int length = message.length();
                if(length < 200){
                    PcIrcDataMessage dataMessage = new PcIrcDataMessage(serverAddress,clientAddress,length,message);
                    ircClient.sendMessage(dataMessage.getMessage());
                }else{
                    PcIrcDataMessage dataMessage = new PcIrcDataMessage(serverAddress,clientAddress,length, message.substring(0,200));
                    ircClient.sendMessage(dataMessage.getMessage());
                    for(int i = 200; i < length; i=i+200){
                        if(i+200 >= length){
                            PcIrcResumeMessage resumeMessage = new PcIrcResumeMessage(serverAddress,clientAddress, message.substring(i));
                            ircClient.sendMessage(resumeMessage.getMessage());
                        }else{
                            PcIrcResumeMessage resumeMessage = new PcIrcResumeMessage(serverAddress,clientAddress, message.substring(i,i+200));
                            ircClient.sendMessage(resumeMessage.getMessage());
                        }
                    }

                }
            }

            @Override
            public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(serverAddress,clientAddress);
                ircClient.sendMessage(disconnectMessage.getMessage());
            }

            @Override
            public boolean acceptExpireTime(long expireTime) {
                return true;
            }

            @Override
            public void channelOpen(boolean wasInitiated) {
                callbackInterface.channelOpen(PaymentChannelClient.this);
            }
        });
    }

    @Override
    public void onMessage(String message) {
        String receiverAddress = message.substring(0,10);
        String messageType = message.substring(20,24);
        PcIrcMessage pcMessage = null;
        if(!messageRegistry.contains(messageType)){
            return;
        }
        pcMessage = messageRegistry.get(messageType).create(message);

        String senderAddress = pcMessage.getSenderAddress();



        if(pcMessage.getClass().equals(PcIrcConnectedMessage.class)){
            PcIrcConnectedMessage connectedMessage = (PcIrcConnectedMessage)pcMessage;
            try{
                foreignKey.verifyMessage(challenge,connectedMessage.getSignature());
                channelClient.connectionOpen();
            }catch (SignatureException e){
                System.out.println(e);
                PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(serverAddress,clientAddress);
                ircClient.sendMessage(disconnectMessage.getMessage());
            }


        }else if(pcMessage.getClass().equals(PcIrcDisconnectMessage.class)) {
            channelClient.connectionClosed();
            closeChannel();

        }else if(pcMessage.getClass().equals(PcIrcDataMessage.class)) {
            PcIrcDataMessage dataMessage = (PcIrcDataMessage) pcMessage;


            dataToBeReceived = dataMessage.getLength() -  dataMessage.getData().length();

            if (dataToBeReceived == 0){
                try{
                    Protos.TwoWayChannelMessage protoMess = Protos.TwoWayChannelMessage.parseFrom(Base64.decode(dataMessage.getData().toCharArray()));
                    channelClient.receiveMessage(protoMess);
                }catch(Exception e){
                    System.out.println();
                }

            }else{
                builder = new StringBuilder();
                builder.append(dataMessage.getData());
            }
        }else if(pcMessage.getClass().equals(PcIrcResumeMessage.class)) {
            PcIrcResumeMessage resumeMessage = (PcIrcResumeMessage) pcMessage;

            if(dataToBeReceived > 0){
                dataToBeReceived = dataToBeReceived - resumeMessage.getData().length();
                builder.append(resumeMessage.getData());
                if(dataToBeReceived == 0){
                    try{
                        Protos.TwoWayChannelMessage protoMess = Protos.TwoWayChannelMessage.parseFrom(Base64.decode(builder.toString().toCharArray()));
                        channelClient.receiveMessage(protoMess);
                    }catch(Exception e){
                        System.out.println();
                    }

                }
            }else{
                PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(senderAddress, receiverAddress);
                ircClient.sendMessage(disconnectMessage.getMessage());
            }
        }
    }


    public void sendPayment(Coin amount, String pcInvoiceId) {
        try {
            ListenableFuture<PaymentIncrementAck> paymentIncrementAckListenableFuture = channelClient.incrementPayment(amount, ByteString.copyFromUtf8(pcInvoiceId), null);
        } catch (ValueOutOfRangeException e) {
            e.printStackTrace();
        }

    }


    public Coin getChannelBalance() {
        return channelClient.state().getValueRefunded();
    }


    public Sha256Hash getChannelHash(){
        return Sha256Hash.of((foreignPubkey+ownPubkey).getBytes());
    }

    /**
     * <p>Gets the {@link PaymentChannelV1ClientState} object which stores the current state of the connection with the
     * server.</p>
     *
     * <p>Note that if you call any methods which update state directly the server will not be notified and channel
     * initialization logic in the connection may fail unexpectedly.</p>
     */
    public PaymentChannelClientState state() {
        return channelClient.state();
    }

    /**
     * Closes the connection, notifying the server it should settle the channel by broadcasting the most recent payment
     * transaction.
     */
    public void settle() {
        // Shutdown is a little complicated.
        //
        // This call will cause the CLOSE message to be written to the wire, and then the destroyConnection() method that
        // we defined above will be called, which in turn will call wireParser.closeConnection(), which in turn will invoke
        // NioClient.closeConnection(), which will then close the socket triggering interruption of the network
        // thread it had created. That causes the background thread to die, which on its way out calls
        // ProtobufConnection.connectionClosed which invokes the connectionClosed method we defined above which in turn
        // then configures the open-future correctly and closes the state object. Phew!
        try {
            channelClient.settle();
            PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(serverAddress,clientAddress);
            ircClient.sendMessage(disconnectMessage.getMessage());
            callbackInterface.channelClosed(PaymentChannelClient.this);
        } catch (IllegalStateException e) {
            // Already closed...oh well
        }
    }

    /**
     * Disconnects the network connection but doesn't request the server to settle the channel first (literally just
     * unplugs the network socket and marks the stored channel state as inactive).
     */
    public void closeChannel() {
        PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(serverAddress,clientAddress);
        ircClient.sendMessage(disconnectMessage.getMessage());

        callbackInterface.channelClosed(PaymentChannelClient.this);

    }
}
