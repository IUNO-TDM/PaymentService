package iuno.tdm.paymentservice;
import com.codeminders.socketio.common.SocketIOException;
import com.codeminders.socketio.server.ConnectionException;
import com.codeminders.socketio.server.ConnectionListener;
import com.codeminders.socketio.server.EventListener;
import com.codeminders.socketio.server.Socket;
import com.codeminders.socketio.server.transport.jetty.JettySocketIOServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import io.swagger.model.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Created by goergch on 06.03.17.
 */
public class PaymentSocketServlet  extends JettySocketIOServlet{
    private Logger logger;
    private Bitcoin bitcoin = Bitcoin.getInstance();
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        logger = LoggerFactory.getLogger(PaymentSocketServlet.class);

        of("/invoices").on(new ConnectionListener() {
            @Override
            public void onConnect(final Socket socket) throws ConnectionException {
                logger.info("new client connected");
                socket.on("room", new EventListener() {
                    @Override
                    public Object onEvent(String name, Object[] args, boolean ackRequested) {

                        logger.debug("Received message" + args);
                        if(args[0].getClass().equals(String.class)){
                            socket.join((String)args[0]);
//                            Invoice invoice = bitcoin.getInvoiceById(UUID.fromString((String)args[0]));

                            return "OK";
                        }
                        return "Wrong format";

                    }
                });
            }
        });


        BitcoinInvoiceCallbackInterface client = new BitcoinInvoiceCallbackInterface() {
            @Override
            public void invoiceStateChanged(UUID invoiceId, String state) {
                try{
                    of("/invoices").in(invoiceId.toString()).emit("StateChange",state);
                }catch (SocketIOException e){
                    logger.error("SocketIOException in invoiceStateChanged ", e);
                }
            }
        };

        bitcoin.registerCallbackInterfaceClient(client);



    }
}
