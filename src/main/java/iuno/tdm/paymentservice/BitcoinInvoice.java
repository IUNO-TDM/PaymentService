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


/**
 * Invoices may be paid by either completing a BIP21 URI in one single transaction
 * or by completing all transfers in one single transaction.
 */

class BitcoinInvoice {
    private final NetworkParameters params = TestNet3Params.get(); // TODO hardcoding this is an ugly hack
    private long totalAmount = 0;
    private long transferAmount = 0;

    private Date expiration;
    private Address payDirect; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    private Address payTransfers;
    Invoice invoice;
    private Logger logger;

    private BitcoinInvoiceCallbackInterface bitcoinInvoiceCallbackInterface = null;

    private Transaction payingTx;
    private Transaction transferTx;

    class PayedAddress {
        final Address address;
        final Coin targetValue;

        PayedAddress(Address a, Coin target) {
            address = a;
            targetValue = target;
        }
    }
    private HashMap<Address, PayedAddress> payedAddresses = new HashMap<>(); // TODO maybe a simple list is enough


    private TransactionConfidence.Listener payingTransactionConfidenceListener =  new TransactionConfidence.Listener() {
        @Override
        public void onConfidenceChanged(TransactionConfidence confidence, ChangeReason reason) {
            if(bitcoinInvoiceCallbackInterface != null){
                State state = mapConfidenceToState(confidence);
                bitcoinInvoiceCallbackInterface.invoiceStateChanged(BitcoinInvoice.this, state);
            }
        }
    };

    /**
     * This constructor checks a new invoice for sanity.
     * @param id unique id of invoice object
     * @param inv invoice as defined in restful api
     * @param addr address for incoming payment (likely the payments service own wallet)
     * @throws IllegalArgumentException thrown if provided invoice contains illegal values
     */
    BitcoinInvoice(UUID id, Invoice inv, Address addr, Address addr2, BitcoinInvoiceCallbackInterface callbackInterface) throws IllegalArgumentException {
        logger = LoggerFactory.getLogger(Bitcoin.class);
        bitcoinInvoiceCallbackInterface = callbackInterface;
        // check sanity of invoice
        totalAmount = inv.getTotalAmount();
        if (totalAmount < Transaction.MIN_NONDUST_OUTPUT.getValue())
            throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

        // check values (transfer shall be lower than totalamount)
        for (AddressValuePair avp : inv.getTransfers()) {
            Address a = Address.fromBase58(params, avp.getAddress());
            long value = avp.getCoin();
            if (value < Transaction.MIN_NONDUST_OUTPUT.getValue())
                throw new IllegalArgumentException("transfer amount to " + avp.getAddress() + " is less than bitcoin minimum dust output");
            transferAmount += value;
            payedAddresses.put(a, new PayedAddress(a, Coin.valueOf(value)));
        }
        if (totalAmount < (transferAmount + Transaction.MIN_NONDUST_OUTPUT.getValue()))
            throw new IllegalArgumentException("total invoice amount minus sum of transfer amounts is dust");

        // expiration date shall be in the future
        expiration = inv.getExpiration();
        if (isExpired())
            throw new IllegalArgumentException("expiration date must be in the future");

        inv.setInvoiceId(id);
        invoice = inv;
        payDirect = addr;
        payTransfers = addr2;

        payedAddresses.put(payTransfers, new PayedAddress(payTransfers, Coin.valueOf(transferAmount)));
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
        return BitcoinURI.convertToBitcoinURI(payDirect, Coin.valueOf(totalAmount), "PaymentService", "all your coins belong to us");
    }

    /**
     * Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.
     * @return the address value/pairs for the invoice as array
     */
    List<AddressValuePair> getTransfers() {
        List<AddressValuePair> transfers = new Vector<>();
        transfers.addAll(invoice.getTransfers());
        long difference = totalAmount - transferAmount;
        transfers.add(new AddressValuePair().address(payTransfers.toBase58()).coin(difference));
        return transfers;
    }

    State getState() {
        TransactionConfidence confidence = null;
        if (null != payingTx) confidence = payingTx.getConfidence();
        return mapConfidenceToState(confidence);
    }

    static public State mapConfidenceToState(TransactionConfidence conf) {
        State result = new State();
        result.setState(State.StateEnum.UNKNOWN);
        result.setDepthInBlocks(Integer.MIN_VALUE);
        if (conf != null) {
            switch (conf.getConfidenceType()) {
                case BUILDING:
                    result.setState(State.StateEnum.BUILDING);
                    result.setDepthInBlocks(conf.getDepthInBlocks());
                    break;
                case PENDING:
                    result.setState(State.StateEnum.PENDING);
                    result.setDepthInBlocks(0);
                    break;
                case DEAD:
                    result.setState(State.StateEnum.DEAD);
                    result.setDepthInBlocks(Integer.MIN_VALUE);
                    break;
                case UNKNOWN:
                default:
            }
        }

        return result;
    }



    Set<TransactionInput> getInputs() {
        Set<TransactionInput> inputs = new HashSet<>();
        for (TransactionOutput tout : payingTx.getOutputs()) {
            if (payDirect.equals(tout.getAddressFromP2PKHScript(params))
                    && tout.isAvailableForSpending()) {
                int index = tout.getIndex();
                TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, payingTx);
                byte[] script = tout.getScriptBytes();
                TransactionInput txin; // TODO check if script needs to contain something
                txin = new TransactionInput(params, payingTx, script, txOutpoint);
                txin.clearScriptBytes();
                inputs.add(txin);
            }
        }
        return inputs;
    }

    SendRequest tryFinishInvoice() {
        UUID invoiceId = invoice.getInvoiceId();
        if (null == payingTx) {
            logger.info("Invoice " + invoiceId.toString() + " has not yet been paid.");
            return null;
        }

        if (null != transferTx) {
            logger.info("Invoice " + invoiceId.toString() + " is already finished.");
            return null;
        }

        Transaction tx = new Transaction(params);

        // add inputs from incoming payment
        for (TransactionInput txin : getInputs())
            tx.addInput(txin);

        // add transfer outputs
        for (PayedAddress pa : payedAddresses.values()) {
            if (! payTransfers.equals(pa.address))
                tx.addOutput(pa.targetValue, pa.address);
        }
        tx.setMemo(invoiceId.toString());
        SendRequest sr = SendRequest.forTx(tx);
        transferTx = tx;
        logger.info(String.format("Forwarding transfers for invoice %s.", invoiceId.toString()));

        return sr;
    }

    private boolean doesTxFulfillTransferPayment(HashMap<Address, Coin> foo) {
        boolean result = true;

        for (PayedAddress pa : payedAddresses.values()) {
            if ( ! (foo.keySet().contains(pa.address))
                    || (pa.targetValue.isGreaterThan(foo.get(pa.address)))) {
                result = false;
            }
        }

        return result;
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     * @deprecated this is an inefficient way that only works for verifying just a few payments per second
     * @param tx new transaction with outputs to be checked
     */
    public void sortOutputsToAddresses(Transaction tx, HashMap<Address, Coin> foo) {

        if (foo.keySet().contains(payDirect)) {
            long value = foo.get(payDirect).getValue();
            if (totalAmount <= value) { // transaction fulfills direct payment
                logger.info("Received direct payment for invoice " + invoice.getInvoiceId().toString()
                        + " to " + payDirect
                        + " with " + foo.get(payDirect).toFriendlyString());
                payingTx = tx;
                payingTx.getConfidence().addEventListener(payingTransactionConfidenceListener);
                payingTransactionConfidenceListener.onConfidenceChanged(payingTx.getConfidence(),null);
            }

        } else if (foo.keySet().contains(payTransfers)) {
            if (doesTxFulfillTransferPayment(foo)) {
                logger.info("Received transfer payment for invoice " + invoice.getInvoiceId().toString()
                        + " to " + payTransfers);
                payingTx = tx;
                transferTx = tx;
                payingTx.getConfidence().addEventListener(payingTransactionConfidenceListener);
            }
        }
    }
}
