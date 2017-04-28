package iuno.tdm.paymentservice;

import io.swagger.model.Transactions;
import org.bitcoinj.core.Sha256Hash;
import io.swagger.model.State;

/**
 * Created by goergch on 26.04.17.
 */
public interface TransactionListStateListener {

    void mostConfidentTxStateChanged(Sha256Hash txHash, State state);

    void transactionsOrStatesChanged(Transactions transactions);
}
