package iuno.tdm.paymentservice;

import io.swagger.model.Transactions;
import org.bitcoinj.core.Sha256Hash;
import io.swagger.model.State;
import org.bitcoinj.core.Transaction;

/**
 * Created by goergch on 26.04.17.
 */
public interface TransactionListStateListener {

    void mostConfidentTxStateChanged(Transaction transactions, State state);

    void transactionsOrStatesChanged(Transactions transactions);
}
