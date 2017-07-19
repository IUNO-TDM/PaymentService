package iuno.tdm.paymentservice.paymentchannel;

import com.subgraph.orchid.encoders.Hex;
import iuno.tdm.paymentservice.Bitcoin;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionBroadcaster;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(Bitcoin.class);

    public PaymentChannel(Wallet wallet, TransactionBroadcaster broadcaster) {
        this.wallet = wallet;
        this.broadcaster = broadcaster;
        ircClient = new IrcClient("iunomachine0","irc.freenode.net","#tdmiunopaymentchannel", this);

    }

    public void addReceivingKey(ECKey ecKey) {
        serverAdresses.put(ecKey.getPublicKeyAsHex().substring(0,10), ecKey);
        logger.info("Added ReceivingKey with Pubkey: " + ecKey.getPublicKeyAsHex());
    }

    public void sendPayment(String pubKey, Coin amount, UUID invoiceId){
        //TODO here comes the logic searching for the right channel to send the amount opening a channel if necessary?

        PaymentChannelClient paymentChannelClient = null;
        //find a matching Channel
        for (PaymentChannelClient client:clientMap.values()) {
            if(client.getForeignPubkey().equals(pubKey)){
                if(client.getChannelBalance().isGreaterThan(amount)) {
                    paymentChannelClient = client;
                }
            }
        }
        if(paymentChannelClient == null ){
            paymentChannelClient = new PaymentChannelClient(pubKey,Coin.CENT,wallet,this,this);
            clientMap.put(paymentChannelClient.getChannelHash(),paymentChannelClient);
        }
        paymentChannelClient.sendPayment(amount,invoiceId.toString());
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
                            serverAdresses.get(receiverAddress),wallet,this, this);
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
        logger.debug("PaymentChannelClient: channel opened");
    }


    @Override
    public void channelClosed(PaymentChannelClient client) {
        logger.debug("PaymentChannelClient: Channel closed");
    }

    @Override
    public void channelOpen(PaymentChannelServer server) {
        logger.debug("PaymentChannelServer: Channel open");
    }

    @Override
    public void receivedPayment(PaymentChannelServer server, Coin amount, String invoiceId) {
        logger.debug("PaymentChannelServer: received payment");
    }

    @Override
    public void channelClosed(PaymentChannelServer server) {
        logger.debug("PaymentChannelServer: channel closed");
    }
}
