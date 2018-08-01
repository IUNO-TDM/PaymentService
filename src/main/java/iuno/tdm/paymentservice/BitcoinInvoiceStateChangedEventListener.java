package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceStateChangedEventListener {
    void onPayingStateChanged(BitcoinInvoice invoice, State state);

    void onTransferStateChanged(BitcoinInvoice invoice, State state);

    @Deprecated
    void onPayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);

    @Deprecated
    void onTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);
}
