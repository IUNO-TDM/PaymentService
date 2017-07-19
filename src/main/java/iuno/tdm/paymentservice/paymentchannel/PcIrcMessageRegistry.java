package iuno.tdm.paymentservice.paymentchannel;

import iuno.tdm.paymentservice.paymentchannel.messages.*;

import java.util.Hashtable;

/**
 * Created by goergch on 22.06.17.
 */
public class PcIrcMessageRegistry {

    private Object knownClassesLock = new Object();
    private Hashtable<String, PcIrcMessageFactoryInterface> knownClasses = new Hashtable<String, PcIrcMessageFactoryInterface>();

    public void registerClass(String identifier, PcIrcMessageFactoryInterface factory) throws Exception {
        synchronized (knownClassesLock){
            if(!knownClasses.containsKey(identifier)){
                knownClasses.put(identifier,factory);
            }else{
                String what = identifier + " already registered.";
                throw new Exception(what);
            }
        }
    }
    public boolean contains(String identifier){
        boolean rv = false;
        synchronized (knownClassesLock){
            rv = knownClasses.containsKey(identifier);
        }
        return rv;
    }

    public PcIrcMessageFactoryInterface get(String identifier){
        PcIrcMessageFactoryInterface factory = null;
        synchronized (knownClassesLock){
            factory = knownClasses.get(identifier);
        }
        return factory;
    }

    public static PcIrcMessageRegistry createStandardRegistry(){
        PcIrcMessageRegistry registry = new PcIrcMessageRegistry();
        try{

            registry.registerClass(PcIrcResumeMessage.getMessageIdentifier(),PcIrcResumeMessage.FACTORY);
            registry.registerClass(PcIrcDataMessage.getMessageIdentifier(),PcIrcDataMessage.FACTORY);
            registry.registerClass(PcIrcDisconnectMessage.getMessageIdentifier(),PcIrcDisconnectMessage.FACTORY);
            registry.registerClass(PcIrcConnectedMessage.getMessageIdentifier(),PcIrcConnectedMessage.FACTORY);
            registry.registerClass(PcIrcConnectMessage.getMessageIdentifier(),PcIrcConnectMessage.FACTORY);
        }catch (Exception e){
            System.out.println(e);
        }
        return registry;
    }
}
