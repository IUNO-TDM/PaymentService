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
import io.swagger.model.Transactions;
import io.swagger.model.TransactionsInner;
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
                            String room = (String) args[0];
                            socket.join((String) args[0]);
                            try {
                                BitcoinInvoice bcInvoice = bitcoin.getBitcoinInvoiceById(UUID.fromString((String) args[0]));
                                String jsonString = buildStateJsonString(bcInvoice.getInvoice(), bcInvoice.getState());
                                socket.emit("StateChange", jsonString);


                                jsonString = buildTransactionsJsonString(bcInvoice.getInvoice(), bcInvoice.getPayingTransactions());
                                socket.emit("PayingTransactionsChange", jsonString);

                                try {
                                    jsonString = buildStateJsonString(bcInvoice.getInvoice(), bcInvoice.getTransferState());
                                    socket.emit("TransferStateChange", jsonString);
                                    jsonString = buildTransactionsJsonString(bcInvoice.getInvoice(), bcInvoice.getTransferTransactions());
                                    socket.emit("TransferTransactionsChange", jsonString);
                                } catch (NoSuchFieldException e) {
                                    //normal for every tx without transfers
                                }


                            } catch (NullPointerException e) {
                                logger.error("The requested Invoice does not exists. Cannot send any Stateupdate at " +
                                        "this moment...maybe later", e);
                            } catch (SocketIOException e) {
                                logger.error("Tried to send a first state", e);
                            }
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
                    String jsonString = buildStateJsonString(invoice, state);
                    String roomId = invoice.getInvoiceId().toString();
                    of("/invoices").in(roomId).emit("StateChange", jsonString);
                } catch (SocketIOException e) {
                    logger.error("SocketIOException in invoiceStateChanged ", e);
                }
            }

            @Override
            public void invoiceTransferStateChanged(Invoice invoice, State state) {
                try {
                    //TODO find more elegant way to generate a JSON object
                    String jsonString = buildStateJsonString(invoice, state);
                    String roomId = invoice.getInvoiceId().toString();
                    of("/invoices").in(roomId).emit("TransferStateChange", jsonString);
                } catch (SocketIOException e) {
                    logger.error("SocketIOException in invoiceStateChanged ", e);
                }
            }

            @Override
            public void invoicePayingTransactionsChanged(Invoice invoice, Transactions transactions) {
                try {
                    //TODO find more elegant way to generate a JSON object
                    String jsonString = buildTransactionsJsonString(invoice, transactions);
                    String roomId = invoice.getInvoiceId().toString();
                    of("/invoices").in(roomId).emit("PayingTransactionsChange", jsonString);
                } catch (SocketIOException e) {
                    logger.error("SocketIOException in PayingTransactionsChange ", e);
                }
            }

            @Override
            public void invoiceTransferTransactionsChanged(Invoice invoice, Transactions transactions) {
                try {
                    //TODO find more elegant way to generate a JSON object
                    String jsonString = buildTransactionsJsonString(invoice, transactions);
                    String roomId = invoice.getInvoiceId().toString();
                    of("/invoices").in(roomId).emit("TransferTransactionsChange", jsonString);
                } catch (SocketIOException e) {
                    logger.error("SocketIOException in TransferTransactionsChange ", e);
                }
            }
        };


        bitcoin.registerCallbackInterfaceClient(client);
    }


    static String buildStateJsonString(Invoice invoice, State state) {
        String jsonString = "{\"invoiceId\":\"" + invoice.getInvoiceId()
                + "\",\"referenceId\":\"" + invoice.getReferenceId()
                + "\",\"state\":\"" + state.getState() + "\",\"depth\":" + state.getDepthInBlocks() + "}";
        return jsonString;
    }

    static String buildTransactionsJsonString(Invoice invoice, Transactions transactions) {

        StringBuilder builder = new StringBuilder();
        builder.append("{\"invoiceId\":\"");
        builder.append(invoice.getInvoiceId());
        builder.append("\",\"transactions\":[");
        boolean first = true;
        for (TransactionsInner tx : transactions) {
            if(first){
                first = false;
            }else {
                builder.append(',');
            }
            builder.append("{\"transaction\":\"");
            builder.append(tx.getTransactionId());
            builder.append("\",\"state\":{\"state\":\"");
            builder.append(tx.getState().getState());
            builder.append("\",\"depthInBlocks\":");
            builder.append(tx.getState().getDepthInBlocks());
            builder.append("}}");
        }

        builder.append("]}");
        return builder.toString();
    }


}
