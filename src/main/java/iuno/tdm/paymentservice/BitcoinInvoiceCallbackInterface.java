package iuno.tdm.paymentservice;

import io.swagger.model.State;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceCallbackInterface {
    public void invoiceStateChanged(BitcoinInvoice invoice, State state);
}
