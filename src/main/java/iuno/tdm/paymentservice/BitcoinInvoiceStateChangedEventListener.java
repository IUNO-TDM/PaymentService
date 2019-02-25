package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;
import org.bitcoinj.core.Transaction;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceStateChangedEventListener {
    String PAYMENTSTATECHANGE = "PaymentStateChange";
    String TRANSFERSTATECHANGE = "TransferStateChange";

    void onInvoiceStateChanged(String eventName, BitcoinInvoice invoice, State state, Transaction tx, Transactions txList);
}
