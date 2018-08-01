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
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Created by goergch on 06.03.17.
 */
public class PaymentSocketIOServlet extends JettySocketIOServlet implements BitcoinInvoiceStateChangedEventListener {
    public static final String PAYMENTSERVLET = "PaymentSocketIoCallback";

    private Logger logger;
    private Bitcoin bitcoin;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        config.getServletContext().setAttribute(PAYMENTSERVLET, this);

        logger = LoggerFactory.getLogger(PaymentSocketIOServlet.class);

        of("/invoices").on(socket -> {
                logger.info("new client connected on PaymentSocketIOServlet");
                socket.on("room", (name, args, ackRequested) -> {
                    if (args[0].getClass().equals(String.class)) {
                        String room = (String) args[0];
                        socket.join(room);
                        return "OK";
                    }
                    return "Wrong format";

                });
                socket.on("leave", (name, args, ackRequested) -> {
                    if (args[0].getClass().equals(String.class)) {
                        socket.leave((String) args[0]);
                        return "OK";
                    }
                    return "Wrong format";
                });
        });
    }

    public void onPayingStateChanged(BitcoinInvoice invoice, State state) {
        invoiceStateChanged(invoice.getInvoice(), state);
    }

    @Override
    public void onTransferStateChanged(BitcoinInvoice invoice, State state) {
        invoiceTransferStateChanged(invoice.getInvoice(), state);
    }

    @Override
    public void onPayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        invoicePayingTransactionsChanged(invoice.getInvoice(), transactions);
    }

    @Override
    public void onTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        invoiceTransferTransactionsChanged(invoice.getInvoice(), transactions);
    }


    public void invoiceStateChanged(Invoice invoice, State state) {
        try {
            String jsonString = buildStateJsonString(invoice, state);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("StateChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in onPayingStateChanged ", e);
        }
    }

    public void invoiceTransferStateChanged(Invoice invoice, State state) {
        try {
            String jsonString = buildStateJsonString(invoice, state);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("TransferStateChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in onPayingStateChanged ", e);
        }
    }

    public void invoicePayingTransactionsChanged(Invoice invoice, Transactions transactions) {
        try {
            String jsonString = buildTransactionsJsonString(invoice, transactions);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("PayingTransactionsChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in PayingTransactionsChange ", e);
        }
    }

    public void invoiceTransferTransactionsChanged(Invoice invoice, Transactions transactions) {
        try {
            String jsonString = buildTransactionsJsonString(invoice, transactions);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("TransferTransactionsChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in TransferTransactionsChange ", e);
        }
    }

    private static String buildStateJsonString(Invoice invoice, State state) {
        return new JSONObject()
                .put("invoiceId", invoice.getInvoiceId())
                .put("referenceId", invoice.getReferenceId())
                .put("state", state.getState())
                .put("depthInBlocks", state.getDepthInBlocks())
                .put("seenByPeers", state.getSeenByPeers())
                .put("depth", state.getDepthInBlocks()) // FIXME: legacy, fix in MarketplaceCore and MixerControl
                .toString();
    }

    private static String buildTransactionsJsonString(Invoice invoice, Transactions transactions) {
        JSONObject bar = new JSONObject()
                .put("invoiceId", invoice.getInvoiceId())
                .put("referenceId", invoice.getReferenceId());

        for (TransactionsInner ti : transactions)
            bar.append("transactions", new JSONObject()
                    .put("transaction", ti.getTransactionId())
                    .put("state", new JSONObject()
                            .put("state", ti.getState().getState())
                            .put("depthInBlocks", ti.getState().getDepthInBlocks())
                            .put("seenByPeers", ti.getState().getSeenByPeers()))
            );

        return bar.toString();
    }
}
