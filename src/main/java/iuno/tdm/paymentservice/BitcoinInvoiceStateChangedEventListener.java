package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;
import org.bitcoinj.core.Transaction;

/**
 * Created by goergch on 08.03.17.
 */
public interface BitcoinInvoiceStateChangedEventListener {
    void onPayingStateChanged(BitcoinInvoice invoice, State state, Transaction tx);

    void onTransferStateChanged(BitcoinInvoice invoice, State state, Transaction tx);

    @Deprecated
    void onPayingTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);

    @Deprecated
    void onTransferTransactionsChanged(BitcoinInvoice invoice, Transactions transactions);
}
