package iuno.tdm.paymentservice;

import io.swagger.model.Invoice;
import io.swagger.model.State;

import java.util.UUID;

/**
 * Created by goergch on 06.03.17.
 */
public interface BitcoinCallbackInterface {
    void invoiceStateChanged(Invoice invoice, State state);

    void invoiceTransferStateChanged(Invoice invoice, State state);
}
