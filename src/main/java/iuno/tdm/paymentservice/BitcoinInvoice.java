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
import org.bitcoinj.core.*;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.uri.BitcoinURI;
import org.bitcoinj.wallet.SendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;

class BitcoinInvoice {
    final NetworkParameters params = TestNet3Params.get(); // TODO hardcoding this is an ugly hack
    private UUID invoiceId;
    private Coin totalAmount = Coin.ZERO;
    private Date expiration;
    private Address payto; // http://bitcoin.stackexchange.com/questions/38947/how-to-get-balance-from-a-specific-address-in-bitcoinj
    Invoice invoice;
    private Logger logger;
    private Coin transferAmount = Coin.ZERO;

    BitcoinInvoice(UUID id, Invoice inv, Address addr) throws IllegalArgumentException {
        logger = LoggerFactory.getLogger(Bitcoin.class);
        // check sanity of invoice
        totalAmount = Coin.valueOf(inv.getTotalAmount());
        if (totalAmount.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
            throw new IllegalArgumentException("invoice amount is less than bitcoin minimum dust output");

        // check values (transfer shall be lower than totalamount)
        for (AddressValuePair avp : inv.getTransfers()) {
            Coin value = Coin.valueOf(avp.getCoin());
            if (value.isLessThan(Transaction.MIN_NONDUST_OUTPUT))
                throw new IllegalArgumentException("transfer amount to " + avp.getAddress() + " is less than bitcoin minimum dust output");
            transferAmount = transferAmount.add(value);
        }
        if (totalAmount.isLessThan(transferAmount))
            throw new IllegalArgumentException("total invoice amount is less than sum of transfer amounts");

        // expiration date shall be in the future
        expiration = inv.getExpiration();
        if (isExpired())
            throw new IllegalArgumentException("expiration date must be in the future");

        invoiceId = id;
        invoice = inv;
        payto = addr;
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

        transfers.add(new AddressValuePair().address(payto.toBase58()).coin(totalAmount.longValue()));
        return transfers;

// TODO this is there for later:
//        transfers.addAll(invoice.getTransfers());
//        Coin difference = totalAmount.subtract(transferAmount);
//        if (difference.isGreaterThan(Transaction.MIN_NONDUST_OUTPUT))
//            transfers.add(new AddressValuePair().address(payto.toBase58()).coin(difference.longValue()));
//        return transfers;
    }

    private SendRequest payTransfers(@Nullable TransactionInput txin) {
        Transaction tx = new Transaction(params);
        if (null != txin) tx.addInput(txin);
        for (AddressValuePair fwd : invoice.getTransfers()) {
            Coin value = Coin.valueOf(fwd.getCoin());
            Address address = Address.fromBase58(params, fwd.getAddress());
            logger.info("forward " + value.toFriendlyString() + " to " + address.toBase58());
            tx.addOutput(value, address);
        }
        return SendRequest.forTx(tx);
    }

    /**
     * Checks all outputs of a transaction for payments to this invoice.
     * @deprecated this is an efficient way that only works for verifying just a few payments per second
     * @param tx new transaction with outputs to be checked
     */
    public List<SendRequest> checkTxForPayment(Transaction tx) {
        List<SendRequest> result = new ArrayList<>();
        for (TransactionOutput tout : tx.getOutputs()) {
            Address dest = tout.getAddressFromP2PKHScript(params);
            if ((payto.equals(dest))
                    && (totalAmount.getValue() <= tout.getValue().getValue())) {
                logger.info("Received payment for invoice " + invoiceId.toString()
                        + " to " + tout.getAddressFromP2PKHScript(params)
                        + " with " + tout.getValue().toFriendlyString());
                int index = tout.getIndex();
                TransactionOutPoint txOutpoint = new TransactionOutPoint(params, index, tx);
                byte[] script = tout.getScriptBytes();
                TransactionInput txin; // TODO check if script needs to contain something
                txin = new TransactionInput(params, tx, script, txOutpoint);
                txin.clearScriptBytes();
                result.add(payTransfers(txin));
            }
        }
        return result;
    }
}
