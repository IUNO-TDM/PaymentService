/**
 * Copyright 2016 TRUMPF Werkzeugmaschinen GmbH + Co. KG
 * Created by Hans-Peter Bock on 01.03.2017.
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package iuno.tdm.paymentservice;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Invoice;
import io.swagger.model.State;
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.SendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

class BitcoinInvoice {
    private final NetworkParameters params = TestNet3Params.get(); // TODO hardcoding this is an ugly hack
    private Coin totalAmount = Coin.ZERO;
    private Coin receivedAmount = Coin.ZERO;
    private Coin transferAmount = Coin.ZERO;
    private boolean transfersFulfilled = false;

    private Date expiration;
    private Address payto; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    Invoice invoice;
    private Logger logger;
    private boolean finished = false;

    class PayedAddress {
        Address address;
        final Coin targetValue;
        Coin receivedValue;
        Vector<TransactionOutput> transactionOutputs = new Vector<>();

        PayedAddress(Address a, Coin target) {
            address = a;
            targetValue = target;
            receivedValue = Coin.ZERO;
        }

        void addTransaction(TransactionOutput txOut) {
            transactionOutputs.add(txOut);
        }

        void updateReceivedValues() { // TODO: maybe it would be a good idea to call back this by a transaction confidence listener
            long received = 0;
            for (TransactionOutput txOut : transactionOutputs) {
                if ( ! txOut.isAvailableForSpending()) continue; // only count unspent outputs
                TransactionConfidence confidence = txOut.getParentTransaction().getConfidence();
                switch (confidence.getConfidenceType()) {
                    case BUILDING:
                    case PENDING:
//                        if (txOut.isAvailableForSpending()) // would be available, not received
                            received += txOut.getValue().getValue();
                    case DEAD:
                    case IN_CONFLICT:
                    case UNKNOWN:
                    default:
                        logger.info(String.format("Transaction %s has confidence type %s.", txOut.getParentTransactionHash().toString(), confidence.toString()));
                }
            }
            receivedValue = Coin.valueOf(received);
        }

        Set<TransactionInput> getInputs() {
            Set<TransactionInput> inputs = new HashSet<>();
            for (TransactionOutput tout : transactionOutputs) {
                if (tout.isAvailableForSpending()) { // TODO should be checked with isMine()...
                    int index = tout.getIndex();
                    Transaction tx = tout.getParentTransaction();
                    TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, tx);
                    byte[] script = tout.getScriptBytes();
                    TransactionInput txin; // TODO check if script needs to contain something
                    txin = new TransactionInput(params, tx, script, txOutpoint);
                    txin.clearScriptBytes();
                    inputs.add(txin);
                }
            }
            return inputs;
        }

        boolean isInComplete() {
            return (targetValue.subtract(receivedValue).isPositive());
        }
    }
    private HashMap<Address, PayedAddress> payedAddresses = new HashMap<>(); // TODO maybe a simple list is enough

    /**
     * This constructor checks a new invoice for sanity.
     * @param id unique id of invoice object
     * @param inv invoice as defined in restful api
     * @param addr address for incoming payment (likely the payments service own wallet)
     * @throws IllegalArgumentException thrown if provided invoice contains illegal values
     */
    BitcoinInvoice(UUID id, Invoice inv, Address addr) throws IllegalArgumentException {
        logger = LoggerFactory.getLogger(Bitcoin.class);
        // check sanity of invoice
        totalAmount = Coin.valueOf(inv.getTotalAmount());
        if (totalAmount.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
            throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

        // check values (transfer shall be lower than totalamount)
        for (AddressValuePair avp : inv.getTransfers()) {
            Address a = Address.fromBase58(params, avp.getAddress());
            Coin value = Coin.valueOf(avp.getCoin());
            if (value.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
                throw new IllegalArgumentException("transfer amount to " + avp.getAddress() + " is less than bitcoin minimum dust output");
            transferAmount = transferAmount.add(value);
            payedAddresses.put(a, new PayedAddress(a, value));
        }
        if (totalAmount.isLessThan(transferAmount))
            throw new IllegalArgumentException("total invoice amount is less than sum of transfer amounts");

        // expiration date shall be in the future
        expiration = inv.getExpiration();
        if (isExpired())
            throw new IllegalArgumentException("expiration date must be in the future");

        inv.setInvoiceId(id);
        invoice = inv;
        payto = addr;

        payedAddresses.put(payto, new PayedAddress(payto, transferAmount));
    }

    /**
     * Checks if the invoice is expired.
     * @return true if invoice is expired
     */
    boolean isExpired() {
        return (expiration.before(new Date()));
    }

    /**
     * Returns a BIP21 payment request string.
     * @return BIP21 payment request string
     */
    String getBip21URI() {
        return BitcoinURI.convertToBitcoinURI(payto, totalAmount, "PaymentService", "all your coins belong to us");
    }

    /**
     * Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.
     * @return the address value/pairs for the invoice as array
     */
    List<AddressValuePair> getTransfers() {
        List<AddressValuePair> transfers = new Vector<>();
        transfers.addAll(invoice.getTransfers());
        Coin difference = totalAmount.subtract(transferAmount);
        if (difference.isGreaterThan(Transaction.MIN_NONDUST_OUTPUT))
            transfers.add(new AddressValuePair().address(payto.toBase58()).coin(difference.longValue()));
        return transfers;
    }

    State getState() {
        State result = new State();

        // lookup input tx
        Vector<TransactionOutput> tos = payedAddresses.get(payto).transactionOutputs;

        TransactionOutput to = tos.lastElement(); // TODO WTF?

        TransactionConfidence tc = to.getParentTransaction().getConfidence();
        switch (tc.getConfidenceType()) {
            case BUILDING:
                result.setState(State.StateEnum.BUILDING);
                result.setDepthInBlocks(tc.getDepthInBlocks());
            case PENDING:
                result.setState(State.StateEnum.PENDING);
                result.setDepthInBlocks(0);
            case DEAD:
                result.setState(State.StateEnum.DEAD);
                result.setDepthInBlocks(Integer.MIN_VALUE);
            case UNKNOWN:
            default:
                result.setState(State.StateEnum.UNKNOWN);
                result.setDepthInBlocks(Integer.MIN_VALUE);
        }


        return result;
    }

    private synchronized void updateValues() {
        long received = 0;
        boolean tf = true;

        for (PayedAddress pa : payedAddresses.values()) {
            pa.updateReceivedValues();
            if (payto.equals(pa.address)) {
                received += pa.receivedValue.getValue();
            } else {
                received += Math.min(pa.receivedValue.getValue(), pa.targetValue.getValue());
                if (pa.isInComplete()) tf = false;
            }
        }
        receivedAmount = Coin.valueOf(received);
        transfersFulfilled = tf;
    }

    SendRequest tryFinishInvoice() {
        UUID invoiceId = invoice.getInvoiceId();
        if (finished) {
            logger.info("Invoice " + invoiceId.toString() + " is already finished.");
            return null;
        }

        updateValues(); // update receivedAmount and spendableAmount

        SendRequest sr = null;
        if (receivedAmount.isLessThan(totalAmount)) { // received too few bitcoins
            logger.info(String.format("Received too few bitcoins to fulfill invoice %s.", invoiceId.toString()));

        } else if (transfersFulfilled) {
            logger.info(String.format("Received enough bitcoins with already fulfilled transfers for invoice %s.", invoiceId.toString()));
            finished = true;

        } else { // fulfill transfers
            Transaction tx = new Transaction(params);

            // get inputs and add missing transfer outputs
            for (PayedAddress pa : payedAddresses.values()) {
                if (payto.equals(pa.address)) { // this is payto
                    for (TransactionInput txIn : pa.getInputs())
                        tx.addInput(txIn);

                } else { // this is a transfer...
                    Coin missingValue = pa.targetValue.subtract(pa.receivedValue);
                    if (missingValue.isGreaterThan(Transaction.MIN_NONDUST_OUTPUT)) { // ...with too few bitcoins
                        tx.addOutput(pa.targetValue.subtract(pa.receivedValue), pa.address);
                    }
                }
            }
            tx.setMemo(invoiceId.toString());
            sr = SendRequest.forTx(tx);
            logger.info(String.format("Forwarding transfers for invoice %s.", invoiceId.toString()));
            finished = true; // TODO better set this to true after the transaction has completed
        }

        return sr;
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     * @deprecated this is an inefficient way that only works for verifying just a few payments per second
     * @param tx new transaction with outputs to be checked
     */
    public void sortOutputsToAddresses(Transaction tx) {
        for (TransactionOutput tout : tx.getOutputs()) {
            Address dest = tout.getAddressFromP2PKHScript(params);
            if (payedAddresses.containsKey(dest)) {
                payedAddresses.get(dest).addTransaction(tout);
                logger.info("Received payment for invoice " + invoice.getInvoiceId().toString()
                        + " to " + tout.getAddressFromP2PKHScript(params)
                        + " with " + tout.getValue().toFriendlyString());
            }
        }
    }
}
