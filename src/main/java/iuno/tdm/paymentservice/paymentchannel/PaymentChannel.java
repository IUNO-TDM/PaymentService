package iuno.tdm.paymentservice.paymentchannel;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.wallet.Wallet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Created by goergch on 18.07.17.
 */
public class PaymentChannel implements IrcClientCallbackInterface, IrcClientInterface, PaymentChannelServerCallbackInterface,PaymentChannelClientCallbackInterface{

    Wallet wallet;
    TransactionBroadcaster broadcaster;

    Map<Sha256Hash, PaymentChannelClient> clientMap = new HashMap<>();
    Map<Sha256Hash, PaymentChannelServer> serverMap = new HashMap<>();
    Map<String, ECKey> serverAdresses = new HashMap<>();

    IrcClient ircClient;


    public PaymentChannel(Wallet wallet, TransactionBroadcaster broadcaster) {
        this.wallet = wallet;
        this.broadcaster = broadcaster;
        ircClient = new IrcClient("test","irc.freenode.net","#tdmiunopaymentchannel", this);
    }

    public void sendPayment(String pubKey, Coin amount, UUID invoiceId, String pcInvoiceId){
        //TODO here comes the logic searching for the right channel to send the amount opening a channel if necessary?
    }

    @Override
    public void onMessage(String message) {
        String receiverAddress = message.substring(0,10);
        String senderAddress = message.substring(10,20);
        Sha256Hash channelHash = Sha256Hash.of((receiverAddress+senderAddress).getBytes());

        if(clientMap.containsKey(channelHash)){
            clientMap.get(channelHash).onMessage(message);
        }else if(serverMap.containsKey(channelHash)){
            serverMap.get(channelHash).onMessage(message);
        }else if(serverAdresses.containsKey(receiverAddress)){
            PaymentChannelServer paymentChannelServer =
                    new PaymentChannelServer(broadcaster,receiverAddress, senderAddress,
                            serverAdresses.get(receiverAddress),wallet,this);
            serverMap.put(channelHash,paymentChannelServer);
            paymentChannelServer.onMessage(message);
        }
    }

    @Override
    public void sendMessage(String message) {
        ircClient.sendMessage(message);
    }

    @Override
    public void channelOpen(PaymentChannelClient client) {

    }

    @Override
    public void receivedPayment(PaymentChannelClient client, Coin amount, String invoiceId) {

    }

    @Override
    public void channelClosed(PaymentChannelClient client) {

    }

    @Override
    public void channelOpen(PaymentChannelServer server) {

    }

    @Override
    public void receivedPayment(PaymentChannelServer server, Coin amount, String invoiceId) {

    }

    @Override
    public void channelClosed(PaymentChannelServer server) {

    }
}
