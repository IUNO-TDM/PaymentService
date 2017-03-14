package iuno.tdm.paymentservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.model.AddressValuePair;
import io.swagger.model.Invoice;

import java.io.Serializable;
import java.util.*;

/**
 * Created by goergch on 10.03.17.
 */
public class SerializableInvoice implements Serializable {


    private Long totalAmount = null;
    private Date expiration = null;

    private HashMap<String,Long> transfers = new HashMap<>();

    private UUID invoiceId = null;

    private String referenceId = null;


    public SerializableInvoice(Invoice invoice){
        totalAmount = invoice.getTotalAmount();
        expiration = invoice.getExpiration();
        invoiceId= invoice.getInvoiceId();
        referenceId = invoice.getReferenceId();

        for(AddressValuePair pair:invoice.getTransfers()){
            transfers.put(pair.getAddress(),pair.getCoin());
        }

    }

    public Invoice getInvcoice(){
        Invoice invoice  = new Invoice();
        invoice.setInvoiceId(invoiceId);
        invoice.setExpiration(expiration);
        invoice.setReferenceId(referenceId);
        invoice.setTotalAmount(totalAmount);
        List<AddressValuePair> list = new ArrayList<>();
        for (HashMap.Entry<String, Long> entry : transfers.entrySet()) {
            AddressValuePair pair = new AddressValuePair();
            pair.setAddress(entry.getKey());
            pair.setCoin(entry.getValue());
            list.add(pair);
        }
        invoice.setTransfers(list);
        return invoice;
    }
}
