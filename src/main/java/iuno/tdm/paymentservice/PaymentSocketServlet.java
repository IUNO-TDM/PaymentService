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
import io.swagger.model.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Created by goergch on 06.03.17.
 */
public class PaymentSocketServlet extends JettySocketIOServlet {
    private Logger logger;
    private Bitcoin bitcoin = Bitcoin.getInstance();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        logger = LoggerFactory.getLogger(PaymentSocketServlet.class);

        of("/invoices").on(new ConnectionListener() {
            @Override
            public void onConnect(final Socket socket) throws ConnectionException {
                logger.info("new client connected on PaymentSocketServlet");
                socket.on("room", new EventListener() {
                    @Override
                    public Object onEvent(String name, Object[] args, boolean ackRequested) {
                        if (args[0].getClass().equals(String.class)) {
                            socket.join((String) args[0]);
//                             Invoice invoice = bitcoin.getInvoiceById(UUID.fromString((String)args[0]));
                            //TODO send current invoice state to new client
                            return "OK";
                        }
                        return "Wrong format";

                    }
                });
                socket.on("leave", new EventListener() {
                    @Override
                    public Object onEvent(String name, Object[] args, boolean ackRequested) {
                        if (args[0].getClass().equals(String.class)) {
                            socket.leave((String) args[0]);

                            return "OK";
                        }
                        return "Wrong format";

                    }
                });
            }
        });


        BitcoinCallbackInterface client = new BitcoinCallbackInterface() {
            @Override
            public void invoiceStateChanged(Invoice invoice, State state) {
                try {
                    //TODO find more elegant way to generate a JSON object
                    String jsonString = buildJsonString(invoice, state);
                    of("/invoices").in(invoice.getInvoiceId().toString()).emit("StateChange", jsonString);
                } catch (SocketIOException e) {
                    logger.error("SocketIOException in invoiceStateChanged ", e);
                }
            }
        };


        bitcoin.registerCallbackInterfaceClient(client);
    }

    static String buildJsonString(Invoice invoice, State state) {
        String jsonString = "{invoiceId:\"" + invoice.getInvoiceId()
                + "\",referenceId:\"" + invoice.getReferenceId()
                + "\",state:\"" + state.toString() + "\"";
        return jsonString;
    }
}
