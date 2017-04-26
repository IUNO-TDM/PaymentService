package iuno.tdm.paymentservice;

import org.bitcoinj.core.Sha256Hash;
import io.swagger.model.State;
import org.bitcoinj.core.TransactionConfidence;

/**
 * Created by goergch on 26.04.17.
 */
public interface TransactionListStateListener {

    void mostConfidentTxStateChanged(Sha256Hash txHash, State state);
}
