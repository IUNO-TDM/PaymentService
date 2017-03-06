package iuno.tdm.paymentservice;

import java.util.UUID;

/**
 * Created by goergch on 06.03.17.
 */
public interface BitcoinInvoiceCallbackInterface {
    public void invoiceStateChanged(UUID invoiceId, String state);
}
