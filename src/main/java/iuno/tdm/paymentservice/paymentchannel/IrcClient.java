package iuno.tdm.paymentservice.paymentchannel;

import org.pircbotx.Configuration;
import org.pircbotx.MultiBotManager;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.MessageEvent;

/**
 * Created by goergch on 18.07.17.
 */
public class IrcClient extends ListenerAdapter implements IrcClientInterface{
    String ircNickName;
    String ircServerAddress;
    String ircRoomName;
    IrcClientCallbackInterface ircClientCallbackInterface;
    private PircBotX botX;

    public IrcClient(String ircNickName, String ircServerAddress, String ircRoomName, IrcClientCallbackInterface ircClientCallbackInterface) {
        this.ircNickName = ircNickName;
        this.ircServerAddress = ircServerAddress;
        this.ircRoomName = ircRoomName;
        this.ircClientCallbackInterface = ircClientCallbackInterface;

        Configuration.Builder templateConfig = new Configuration.Builder()
                .setName(ircNickName)
                .setAutoNickChange(true)
                .addListener(this)
                .addAutoJoinChannel(ircRoomName);

        MultiBotManager manager = new MultiBotManager();
        manager.addBot(templateConfig.buildForServer(ircServerAddress));
        manager.start();
        botX = manager.getBotById(0);

    }

    public void sendMessage(String message){
        botX.send().message(ircRoomName,message);
    }

    @Override
    public void onMessage(MessageEvent event) throws Exception {
        ircClientCallbackInterface.onMessage(event.getMessage());
        super.onMessage(event);
    }
}
