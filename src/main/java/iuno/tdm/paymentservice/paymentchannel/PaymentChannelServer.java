package iuno.tdm.paymentservice.paymentchannel;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.lambdaworks.codec.Base64;
import com.subgraph.orchid.encoders.Hex;
import iuno.tdm.paymentservice.paymentchannel.messages.*;
import org.bitcoin.paymentchannel.Protos;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;
import org.bitcoinj.protocols.channels.ServerConnectionEventHandler;
import org.bitcoinj.wallet.Wallet;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Created by goergch on 18.07.17.
 */
public class PaymentChannelServer implements IrcClientCallbackInterface {

    private final PcIrcMessageRegistry messageRegistry;
    private TransactionBroadcaster broadcaster;
    private String ownPubkey;
    private String foreignPubkey;
    private ECKey serverKey;
    private Wallet wallet;
    private PaymentChannelServerCallbackInterface callbackInterface;
    private IrcClientInterface ircClientInterface;
    private int dataToBeReceived = 0;
    private StringBuilder builder;
    private org.bitcoinj.protocols.channels.PaymentChannelServer paymentChannelManager;
    private PaymentChannelCloseException.CloseReason closeReason;
    private Coin balance = Coin.valueOf(0);

    public PaymentChannelServer(TransactionBroadcaster broadcaster, final String ownPubkey,
                                final String foreignPubkey, ECKey serverKey, Wallet wallet,
                                final PaymentChannelServerCallbackInterface callbackInterface,
                                final IrcClientInterface ircClientInterface) {
        this.broadcaster = broadcaster;
        this.ownPubkey = ownPubkey;
        this.foreignPubkey = foreignPubkey;
        this.serverKey = serverKey;
        this.wallet = wallet;
        this.callbackInterface = callbackInterface;
        this.ircClientInterface = ircClientInterface;

        this.messageRegistry = PcIrcMessageRegistry.createStandardRegistry();
        paymentChannelManager =
                new org.bitcoinj.protocols.channels.PaymentChannelServer(
                        broadcaster, wallet, Coin.valueOf(1000000), new org.bitcoinj.protocols.channels.PaymentChannelServer.ServerConnection() {
                    @Override
                    public void sendToClient(Protos.TwoWayChannelMessage msg) {
                        String message =  String.copyValueOf(Base64.encode(msg.toByteArray()));
                        int length = message.length();
                        if(length < 200){
                            PcIrcDataMessage dataMessage = new PcIrcDataMessage(foreignPubkey,ownPubkey,length,message);
                            ircClientInterface.sendMessage(dataMessage.getMessage());
                        }else{
                            PcIrcDataMessage dataMessage = new PcIrcDataMessage(foreignPubkey,ownPubkey,length, message.substring(0,200));
                            ircClientInterface.sendMessage(dataMessage.getMessage());
                            for(int i = 200; i < length; i=i+200){
                                if(i+200 >= length){
                                    PcIrcResumeMessage resumeMessage = new PcIrcResumeMessage(foreignPubkey,ownPubkey, message.substring(i));
                                    ircClientInterface.sendMessage(resumeMessage.getMessage());
                                }else{
                                    PcIrcResumeMessage resumeMessage = new PcIrcResumeMessage(foreignPubkey,ownPubkey, message.substring(i,i+200));
                                    ircClientInterface.sendMessage(resumeMessage.getMessage());
                                }
                            }

                        }
                    }

                    @Override
                    public void destroyConnection(PaymentChannelCloseException.CloseReason reason) {
                        PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(foreignPubkey,ownPubkey);
                        ircClientInterface.sendMessage(disconnectMessage.getMessage());
                    }

                    @Override
                    public void channelOpen(Sha256Hash contractHash) {
                        callbackInterface.channelOpen(PaymentChannelServer.this);
                    }

                    @Nullable
                    @Override
                    public ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, @Nullable ByteString info) {
                        balance = to;
                        String invoiceId = "";
                        if(info != null){
                            invoiceId = info.toString();
                        }
                        callbackInterface.receivedPayment(PaymentChannelServer.this,by,invoiceId);
                        return null;
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

        if(pcMessage.getClass().equals(PcIrcConnectMessage.class)){
            PcIrcConnectMessage connectMessage = (PcIrcConnectMessage)pcMessage;

            if(!connectMessage.getReceiverPubKey().equals(serverKey.getPublicKeyAsHex())){
                //TODO log something
                return;
            }
            connectionOpen(connectMessage.getChallenge());
        }else if(pcMessage.getClass().equals(PcIrcDisconnectMessage.class)) {
            connectionClosed();
        }else if(pcMessage.getClass().equals(PcIrcDataMessage.class)) {
            PcIrcDataMessage dataMessage = (PcIrcDataMessage) pcMessage;


            dataToBeReceived = dataMessage.getLength() -  dataMessage.getData().length();

            if (dataToBeReceived == 0){
                try{
                    Protos.TwoWayChannelMessage protoMess = Protos.TwoWayChannelMessage.parseFrom(Base64.decode(dataMessage.getData().toCharArray()));
                    paymentChannelManager.receiveMessage(protoMess);
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
                        paymentChannelManager.receiveMessage(protoMess);
                    }catch(Exception e){
                        System.out.println();
                    }

                }
            }else{
                PcIrcDisconnectMessage disconnectMessage = new PcIrcDisconnectMessage(senderAddress, receiverAddress);
                ircClientInterface.sendMessage(disconnectMessage.getMessage());
            }
        }
    }
    public Coin getChannelBalance(String pubKey) {
        return paymentChannelManager.state().getFeePaid();
    }

    public synchronized void connectionOpen(String challenge) {


        String signedMessage = serverKey.signMessage(challenge);

        PcIrcConnectedMessage connectedMessage = new PcIrcConnectedMessage(foreignPubkey, ownPubkey,signedMessage);
        ircClientInterface.sendMessage(connectedMessage.getMessage());

        callbackInterface.channelOpen(PaymentChannelServer.this);
        paymentChannelManager.connectionOpen();
    }

    public synchronized void connectionClosed() {
        paymentChannelManager.connectionClosed();
        callbackInterface.channelClosed(PaymentChannelServer.this);
    }
}
