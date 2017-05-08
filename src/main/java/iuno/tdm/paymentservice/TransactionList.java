package iuno.tdm.paymentservice;

import io.swagger.model.State;
import io.swagger.model.Transactions;
import io.swagger.model.TransactionsInner;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by goergch on 26.04.17.
 * Handles a set of Transactions. The List can track the Confidence / State.
 */
public class TransactionList implements TransactionConfidence.Listener {

    Map<Sha256Hash, Transaction> transactions = new HashMap();
    CopyOnWriteArrayList<TransactionListStateListener> listeners = new CopyOnWriteArrayList();

    State lastState = null;
    Sha256Hash lastMostConfidentTxHash = null;
    Transactions lastTransactions = null;

    private static final int UNKNOWN_RATING = 0;
    private static final int DEAD_RATING = 1;
    private static final int IN_CONFLICT_RATING = 2;
    private static final int PENDING_RATING = 3;
    private static final int BUILDING_RATING = 4;
    private static final Logger logger = LoggerFactory.getLogger(TransactionList.class);


    /**
     * Add a transaction to the tracked list. The TransactionList automatically adds itself as a confidence listener
     * at this transaction
     * @param transaction
     */
    public void add(Transaction transaction) {
        logger.debug("Adding tx %s to TransactionList", transaction.getHashAsString());
        if (!transactions.containsKey(transaction.getHash())) {
            transactions.put(transaction.getHash(), transaction);
            transaction.getConfidence().addEventListener(this);
        }
        logger.debug("This list has now %d transactions", transactions.size());
        informStateListenersTransactionsChanged(getTransactions());
    }


    public void addStateListener(TransactionListStateListener listener) {
        listeners.add(listener);
    }

    public void removeStateListener(TransactionListStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Determines the most confident TransactionConfidence of the transaction in its list
     * @return The TransactionConfidence as State
     */
    public State getState() {
        return mapConfidenceToState(determineMostConfidentTransactionConfidence());
    }

    /**
     *
     * @return true if one or more of the transactions in the List have e Confidence Pending or Building
     */
    public boolean isOneOrMoreTxPending() {
        TransactionConfidence confidence = determineMostConfidentTransactionConfidence();
        return (confidence != null && mapConfidenceTypeRating(confidence.getConfidenceType()) >= PENDING_RATING);
    }

    /**
     *
     * @param minDepth the minimal Block Depth
     * @return true if one or more of the transactions in the List have e Confidence Building
     * and have a Block Depth >= minDepth
     */
    public boolean isOneOrMoreTxConfirmed(int minDepth) {
        TransactionConfidence confidence = determineMostConfidentTransactionConfidence();
        return (confidence != null && mapConfidenceTypeRating(confidence.getConfidenceType()) >= BUILDING_RATING && minDepth <= confidence.getDepthInBlocks());
    }

    /**
     *
     * @return the transaction object with the best TransactinoConfidence
     */
    public Transaction getMostConfidentTransaction() {
        return transactions.get(determineMostConfidentTransactionConfidence().getTransactionHash());
    }


    /**
     * A List of Transactions with their state.
     * @return A Transactions Object as used in the swagger REST api
     */
    public Transactions getTransactions() {
        Transactions txes = new Transactions();
        for (Map.Entry<Sha256Hash, Transaction> element : transactions.entrySet()) {
            TransactionsInner transactionsInner = new TransactionsInner();
            transactionsInner.setTransactionId(element.getKey().toString());
            transactionsInner.setState(mapConfidenceToState(element.getValue().getConfidence()));
            txes.add(transactionsInner);
        }
        return txes;
    }

    private TransactionConfidence determineMostConfidentTransactionConfidence() {
        Transaction mostConfidentTx = null;
        for (Transaction tx : transactions.values()) {
            if (mostConfidentTx == null) {
                mostConfidentTx = tx;
            } else {
                TransactionConfidence txConfidence = tx.getConfidence();
                TransactionConfidence mostConfidentTxConfidence = mostConfidentTx.getConfidence();
                int txRating = mapConfidenceTypeRating(txConfidence.getConfidenceType());
                int mcTxRating = mapConfidenceTypeRating(mostConfidentTxConfidence.getConfidenceType());

                if (txRating > mcTxRating) {
                    mostConfidentTx = tx;
                } else if (txRating == mcTxRating) {
                    if (txConfidence.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING) {
                        if (txConfidence.numBroadcastPeers() > mostConfidentTxConfidence.numBroadcastPeers()) {
                            mostConfidentTx = tx;
                        }
                    } else if (txConfidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                        if (txConfidence.getDepthInBlocks() > mostConfidentTxConfidence.getDepthInBlocks()) {
                            mostConfidentTx = tx;
                        }
                    }
                }

            }
        }
        TransactionConfidence conf = null;
        if (mostConfidentTx != null) {
            conf = mostConfidentTx.getConfidence();
        }
        return conf;
    }

    static private int mapConfidenceTypeRating(TransactionConfidence.ConfidenceType type) {
        int rv;
        switch (type) {
            case UNKNOWN:
                rv = UNKNOWN_RATING;
                break;
            case DEAD:
                rv = DEAD_RATING;
                break;
            case IN_CONFLICT:
                rv = IN_CONFLICT_RATING;
                break;
            case PENDING:
                rv = PENDING_RATING;
                break;
            case BUILDING:
                rv = BUILDING_RATING;
                break;
            default:
                rv = UNKNOWN_RATING;
                break;
        }
        return rv;
    }

    /**
     * This method maps a bitcoin transaction confidence object to an object defined using swagger.
     *
     * @param confidence a bitcoinj TransactionConfidence object
     * @return confidence state as swagger object
     */
    static private State mapConfidenceToState(TransactionConfidence confidence) {
        State result = new State();
        result.setState(State.StateEnum.UNKNOWN);
        result.setDepthInBlocks(Integer.MIN_VALUE);
        if (confidence != null) {
            switch (confidence.getConfidenceType()) {
                case BUILDING:
                    result.setState(State.StateEnum.BUILDING);
                    result.setDepthInBlocks(confidence.getDepthInBlocks());
                    break;
                case PENDING:
                    result.setState(State.StateEnum.PENDING);
                    result.setDepthInBlocks(Integer.MIN_VALUE + confidence.numBroadcastPeers());
                    break;
                case DEAD:
                    result.setState(State.StateEnum.DEAD);
                    result.setDepthInBlocks(Integer.MIN_VALUE);
                    break;
                case IN_CONFLICT:
                    result.setState(State.StateEnum.CONFLICT);
                    result.setDepthInBlocks(Integer.MIN_VALUE);
                    break;
                case UNKNOWN:
                default:
            }
        }

        return result;
    }

    static private boolean statesAreDifferent(State state1, State state2) {
        boolean rv = false;
        if (!state1.getState().equals(state2.getState()) || state1.getDepthInBlocks().equals(state2.getDepthInBlocks())) {
            rv = true;
        }
        return rv;
    }

    static private boolean transactionsAreDifferent(Transactions transactions1, Transactions transactions2) {
        boolean rv = false;
        if (transactions1 == null ^ transactions2 == null) {
            rv = true;
        } else if (transactions1 != null && transactions2 != null) {
            if (transactions1.size() != transactions2.size()) {
                rv = true;
            } else {
                boolean noMatch = false;
                for (TransactionsInner tx1 : transactions1) {
                    noMatch = true;
                    for (TransactionsInner tx2 : transactions2) {
                        if (tx1.getTransactionId().equals(tx2.getTransactionId()) && !statesAreDifferent(tx1.getState(), tx2.getState())) {
                            noMatch = false;
                            break;
                        }
                    }
                    if (noMatch) {
                        rv = true;
                        break;
                    }
                }
            }
        }
        return rv;
    }

    protected void cleanup() {
        for (Transaction tx : transactions.values()) {
            tx.getConfidence().removeEventListener(this);
        }
    }


    @Override
    protected void finalize() throws Throwable {
        try {
            cleanup();
        } finally {
            super.finalize();
        }

    }

    @Override
    public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {

        logger.info(String.format(" tx %s changed state to (%s, %d, %d) for change reason %s",
                confidence.getTransactionHash().toString(),
                confidence.getConfidenceType().toString(),
                confidence.numBroadcastPeers(),
                confidence.getDepthInBlocks(),
                reason.toString()));

        boolean refreshNeeded = false;
        if (lastState == null || lastMostConfidentTxHash == null) {
            lastState = mapConfidenceToState(confidence);
            lastMostConfidentTxHash = confidence.getTransactionHash();
            refreshNeeded = true;
        } else {
            TransactionConfidence newMostConfidentConfidence = determineMostConfidentTransactionConfidence();
            State newState = mapConfidenceToState(newMostConfidentConfidence);
            Sha256Hash newTxHash = newMostConfidentConfidence.getTransactionHash();
            if (lastMostConfidentTxHash != newTxHash || statesAreDifferent(lastState, newState)) {
                lastMostConfidentTxHash = newTxHash;
                lastState = newState;
                refreshNeeded = true;
            }
        }
        if (refreshNeeded) {
            informStateListenersMostConfidentState(lastMostConfidentTxHash, lastState);
        }
        Transactions newTransactions = getTransactions();
        if (transactionsAreDifferent(newTransactions, lastTransactions)) {
            lastTransactions = newTransactions;
            informStateListenersTransactionsChanged(lastTransactions);
        }

    }

    private void informStateListenersMostConfidentState(Sha256Hash txHash, State newState) {
        for (TransactionListStateListener listener : listeners) {
            listener.mostConfidentTxStateChanged(txHash, newState);
        }
    }


    private void informStateListenersTransactionsChanged(Transactions transactions) {
        for (TransactionListStateListener listener : listeners) {
            listener.transactionsOrStatesChanged(transactions);
        }
    }

}
