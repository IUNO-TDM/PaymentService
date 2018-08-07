package io.swagger.api.impl;

import io.swagger.api.ApiResponseMessage;
import io.swagger.api.InvoicesApiService;
import io.swagger.api.NotFoundException;
import io.swagger.model.*;
import io.swagger.model.Error;
import iuno.tdm.paymentservice.Bitcoin;
import org.bitcoinj.core.AddressFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-25T13:00:10.686Z")
public class InvoicesApiServiceImpl extends InvoicesApiService {
    private static final Logger logger = LoggerFactory.getLogger(Bitcoin.class);

    @Override
    public Response addCouponToInvoice(UUID invoiceId, Coupon coupon, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            AddressValuePair avp = Bitcoin.getInstance().addCoupon(invoiceId, coupon.getCoupon());
            resp = Response.ok().entity(avp).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp =  Response.status(404).entity(err).build();

        } catch (IllegalStateException e) { // invoice finished or expired
            err.setMessage(String.format("%s %s", e.getMessage(), invoiceId));
            resp = Response.status(409).entity(err).build();

        } catch (AddressFormatException e) { // unparseable coupon code
            err.setMessage("the passed coupon code is invalid");
            resp = Response.status(422).entity(err).build();

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            err.setMessage("could not get coupon value from remote host for " + invoiceId);
            resp = Response.status(503).entity(err).build();
        }
        logger.info(String.format("%s (%03d) addCouponToInvoice: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response addInvoice(Invoice invoice, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        UUID invoiceId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        final Bitcoin bc = Bitcoin.getInstance();
        // send service unavailable (503) if bitcoin peergroup is not connected
        if (false == bc.isRunning()) {
            err.setMessage("Peergroup is unavailable.");
            resp = Response.status(503).entity(err).build();

        } else if (null == invoice) {
            err.setMessage("Invoice object must not be null.");
            resp = Response.status(400).entity(err).build();

        }else {
            URI createdUri = null;
            try {
                invoiceId = bc.addInvoice(invoice);
                createdUri = new URI("http://localhost:8080/v1/invoices/" + invoiceId.toString() + "/");
                resp = Response.created(createdUri).entity(invoice).build();

            } catch (IllegalArgumentException e) { // error in invoice
                err.setMessage(e.getMessage());
                resp = Response.status(400).entity(err).build();

            } catch (URISyntaxException e) { // should never happen
                e.printStackTrace();
                resp = Response.serverError().build();
            }
        }
        logger.info(String.format("%s (%03d) addInvoice: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response deleteInvoiceById(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            Bitcoin.getInstance().deleteInvoiceById(invoiceId);
            resp = Response.ok().entity("invoice deleted").type(MediaType.TEXT_PLAIN_TYPE).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) deleteInvoiceById: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceBip21(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            String bip21 = Bitcoin.getInstance().getInvoiceBip21(invoiceId);
            resp = Response.ok().entity(bip21).type(MediaType.TEXT_PLAIN_TYPE).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceBip21: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceById(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            Invoice invoice = Bitcoin.getInstance().getBitcoinInvoiceById(invoiceId).getInvoice();
            resp = Response.ok().entity(invoice).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceById: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceCouponBalance(UUID invoiceId, String couponAddress, SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
    @Override
    public Response getInvoiceCoupons(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
    @Override
    public Response getInvoicePayingTransactions(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            Transactions transactions = Bitcoin.getInstance().getInvoicePaymentTransactions(invoiceId);
            resp = Response.ok().entity(transactions).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceState: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceState(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            State state = Bitcoin.getInstance().getInvoiceState(invoiceId);
            resp = Response.ok().entity(state).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceState: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceTransferState(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            State state = Bitcoin.getInstance().getInvoiceTransferState(invoiceId);
            resp = Response.ok().entity(state).build();
        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        } catch (NoSuchFieldException e) {
            err.setMessage("Invoice " + invoiceId + ": " + e.getMessage());
            resp = Response.status(423  ).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceTransferState: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }

    @Override
    public Response getInvoiceTransferTransactions(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            Transactions transactions = Bitcoin.getInstance().getInvoiceTransferTransactions(invoiceId);
            resp = Response.ok().entity(transactions).build();
        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        } catch (NoSuchFieldException e) {
            err.setMessage("Invoice " + invoiceId + ": " + e.getMessage());
            resp = Response.status(423  ).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceTransferState: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoiceTransfers(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        Response resp;
        Error err = new Error();
        err.setMessage("success");
        try {
            List<AddressValuePair> transfers = Bitcoin.getInstance().getInvoiceTransfers(invoiceId);
            resp = Response.ok().entity(transfers).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            resp = Response.status(404).entity(err).build();
        }
        logger.info(String.format("%s (%03d) getInvoiceTransfers: %s", invoiceId, resp.getStatus(), err.getMessage()));
        return resp;
    }
    @Override
    public Response getInvoices(SecurityContext securityContext) throws NotFoundException {
        Set<UUID> invoiceIds = Bitcoin.getInstance().getInvoiceIds();
        logger.info(String.format("00000000-0000-0000-0000-000000000000 (200) getInvoices"));
        return Response.ok().entity(invoiceIds).build();
    }
}
