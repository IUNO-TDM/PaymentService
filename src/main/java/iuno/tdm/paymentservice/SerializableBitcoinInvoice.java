package iuno.tdm.paymentservice;

import io.swagger.model.Invoice;
import iuno.tdm.paymentservice.BitcoinInvoice;
import org.bitcoinj.core.Address;

import java.io.Serializable;

/**
 * Created by goergch on 10.03.17.
 */
public class SerializableBitcoinInvoice implements Serializable{

    private SerializableInvoice serializableInvoice;

    private Address payDirectAddress;

    private Address payTransfersAddress;

    private String payingTxHash;

    private String transferTxHash;

    public Invoice getInvoice() {
        return serializableInvoice.getInvcoice();
    }

    public Address getPayDirectAddress() {
        return payDirectAddress;
    }

    public Address getPayTransfersAddress() {
        return payTransfersAddress;
    }

    public String getPayingTxHash() {
        return payingTxHash;
    }

    public String getTransferTxHash() {
        return transferTxHash;
    }




    public SerializableBitcoinInvoice(BitcoinInvoice invoice){
        serializableInvoice = new SerializableInvoice(invoice.getInvoice());
        payDirectAddress =invoice.getPayDirectAddress();
        payTransfersAddress = invoice.getPayTransfersAddress();
        if (invoice.getPayingTx() != null){
            payingTxHash = invoice.getPayingTx().getHashAsString();
        }
        if(invoice.getTransferTx() != null){
            transferTxHash = invoice.getTransferTx().getHashAsString();
        }

    }



}
