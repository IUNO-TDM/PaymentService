package io.swagger.api.impl;

import io.swagger.api.*;
import io.swagger.model.*;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Error;
import io.swagger.model.Invoice;
import io.swagger.model.State;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.WrongNetworkException;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.validation.constraints.*;
import iuno.tdm.paymentservice.Bitcoin;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-03-07T13:08:18.801Z")
public class InvoicesApiServiceImpl extends InvoicesApiService {

    @Override
    public Response addCouponToInvoice(UUID invoiceId, String coupon, SecurityContext securityContext) throws NotFoundException {
        Error err = new Error();
        try {
            AddressValuePair avp = Bitcoin.getInstance().addCoupon(invoiceId, coupon);
            return Response.ok().entity(avp).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();

        } catch (IllegalStateException e) { // invoice finished or expired
            err.setMessage(String.format("%s %s", e.getMessage(), invoiceId));
            return Response.status(409).entity(err).build();

        } catch (AddressFormatException e) { // unparseable coupon code
            err.setMessage("the passed coupon code is invalid");
            return Response.status(422).entity(err).build();

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            err.setMessage("could not get coupon value from remote host for " + invoiceId);
            return Response.status(503).entity(err).build();
        }
    }
    @Override
    public Response addInvoice(Invoice invoice, SecurityContext securityContext) throws NotFoundException {
        final Bitcoin bc = Bitcoin.getInstance();
        // send service unavailable (503) if bitcoin peergroup is not connected
        if (false == bc.isRunning()) {
            Error err = new Error();
            err.setMessage("Peergroup is unavailable.");
            return Response.status(503).entity(err).build();
        }

        URI createdUri = null;
        try {
            UUID invoiceID = bc.addInvoice(invoice);
            createdUri = new URI("http://localhost:8080/v1/invoices/" + invoiceID.toString() + "/");
            return Response.created(createdUri).entity(invoice).build();

        } catch (IllegalArgumentException e) { // error in invoice
            Error err = new Error();
            err.setMessage(e.getMessage());
            return Response.status(400).entity(err).build();

        } catch (URISyntaxException e) { // should never happen
            e.printStackTrace();
            return Response.serverError().build();
        }
    }
    @Override
    public Response deleteInvoiceById(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        try {
            Bitcoin.getInstance().deleteInvoiceById(invoiceId);
            return Response.ok().entity("invoice deleted").type(MediaType.TEXT_PLAIN_TYPE).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceBip21(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        try {
            String bip21 = Bitcoin.getInstance().getInvoiceBip21(invoiceId);
            return Response.ok().entity(bip21).type(MediaType.TEXT_PLAIN_TYPE).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceById(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        try {
            Invoice invoice = Bitcoin.getInstance().getBitcoinInvoiceById(invoiceId).getInvoice();
            return Response.ok().entity(invoice).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
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
    public Response getInvoiceState(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        try {
            State state = Bitcoin.getInstance().getInvoiceState(invoiceId);
            return Response.ok().entity(state).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceTransfers(UUID invoiceId, SecurityContext securityContext) throws NotFoundException {
        try {
            List<AddressValuePair> transfers = Bitcoin.getInstance().getInvoiceTransfers(invoiceId);
            return Response.ok().entity(transfers).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoices(SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
}
