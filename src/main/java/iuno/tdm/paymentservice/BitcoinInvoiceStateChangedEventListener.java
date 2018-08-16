package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;
import org.bitcoinj.core.Transaction;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceStateChangedEventListener {
    // FIXME combine these two nearly identical listeners into one
    void onPaymentStateChanged(BitcoinInvoice invoice, State state, Transaction tx, Transactions txList);
    void onTransferStateChanged(BitcoinInvoice invoice, State state, Transaction tx, Transactions txList);
}
