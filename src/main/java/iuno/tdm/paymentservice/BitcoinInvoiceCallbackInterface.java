package iuno.tdm.paymentservice;

import io.swagger.model.State;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceCallbackInterface {
    void invoiceStateChanged(BitcoinInvoice invoice, State state);
    void invoiceTransferStateChanged(BitcoinInvoice invoice, State state);
}
