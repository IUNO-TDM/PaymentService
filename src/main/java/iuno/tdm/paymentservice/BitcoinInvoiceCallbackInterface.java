package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceCallbackInterface {
    void invoiceStateChanged(BitcoinInvoice invoice, State state);

    void invoiceTransferStateChanged(BitcoinInvoice invoice, State state);

    void invoicePayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);

    void invoiceTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);
}
