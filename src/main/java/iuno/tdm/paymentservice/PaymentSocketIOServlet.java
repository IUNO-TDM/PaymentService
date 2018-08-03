package iuno.tdm.paymentservice;

import com.codeminders.socketio.common.SocketIOException;
import com.codeminders.socketio.server.transport.jetty.JettySocketIOServlet;
import io.swagger.model.Invoice;
import io.swagger.model.State;
import io.swagger.model.Transactions;
import io.swagger.model.TransactionsInner;
import org.bitcoinj.core.Transaction;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

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

    @Override
    public void onPaymentStateChanged(BitcoinInvoice invoice, State state, Transaction tx, Transactions txList) {
        stateChanged("StateChange", invoice.getInvoice(), state, tx, txList); // deprecated
        stateChanged("PaymentStateChange", invoice.getInvoice(), state, tx, txList);
    }

    @Override
    public void onTransferStateChanged(BitcoinInvoice invoice, State state, Transaction tx, Transactions txList) {
        stateChanged("TransferStateChange", invoice.getInvoice(), state, tx, txList);
    }

    @Deprecated
    @Override
    public void onPayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        invoicePayingTransactionsChanged(invoice.getInvoice(), transactions);
    }

    @Deprecated
    @Override
    public void onTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions) {
        invoiceTransferTransactionsChanged(invoice.getInvoice(), transactions);
    }

    public void stateChanged(String eventName, Invoice invoice, State state, Transaction tx, Transactions txList) {
        try {
            String jsonString = buildStateJsonString(invoice, state, tx, txList);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit(eventName, jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in onPayingStateChanged ", e);
        }
    }

    @Deprecated
    public void invoicePayingTransactionsChanged(Invoice invoice, Transactions transactions) {
        try {
            String jsonString = buildTransactionsJsonString(invoice, transactions);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("PayingTransactionsChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in PayingTransactionsChange ", e);
        }
    }

    @Deprecated
    public void invoiceTransferTransactionsChanged(Invoice invoice, Transactions transactions) {
        try {
            String jsonString = buildTransactionsJsonString(invoice, transactions);
            String roomId = invoice.getInvoiceId().toString();
            of("/invoices").in(roomId).emit("TransferTransactionsChange", jsonString);
        } catch (SocketIOException e) {
            logger.error("SocketIOException in TransferTransactionsChange ", e);
        }
    }

    private static String buildStateJsonString(Invoice invoice, State state, Transaction tx, Transactions txList) {
        JSONObject jsonObject = new JSONObject()
                .put("invoiceId", invoice.getInvoiceId())
                .put("referenceId", invoice.getReferenceId())
                .put("state", state.getState())
                .put("depthInBlocks", state.getDepthInBlocks())
                .put("seenByPeers", state.getSeenByPeers())
                .put("txid", tx.getHashAsString())
                .put("depth", state.getDepthInBlocks()); // FIXME: remove this deprecated key/value as soon as PR https://github.com/IUNO-TDM/MarketplaceCore/pull/208 is merged

        for (TransactionsInner ti : txList)
            jsonObject.append("transactions", new JSONObject()
                    .put("transaction", ti.getTransactionId())
                    .put("state", new JSONObject()
                            .put("state", ti.getState().getState())
                            .put("depthInBlocks", ti.getState().getDepthInBlocks())
                            .put("seenByPeers", ti.getState().getSeenByPeers()))
            );

         return jsonObject.toString();
    }

    @Deprecated
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
