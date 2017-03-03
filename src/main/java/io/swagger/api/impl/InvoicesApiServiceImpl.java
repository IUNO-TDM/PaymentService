package io.swagger.api.impl;

import io.swagger.api.*;
import io.swagger.model.*;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Coupon;
import io.swagger.model.Error;
import io.swagger.model.Invoice;
import io.swagger.model.InvoiceId;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;
import java.util.UUID;

import iuno.tdm.paymentservice.Bitcoin;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import javax.validation.constraints.Null;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-03-02T14:39:00.989Z")
public class InvoicesApiServiceImpl extends InvoicesApiService {
    private final Bitcoin bc = Bitcoin.getInstance();

    @Override
    public Response addCouponToInvoice(String invoiceId, Coupon coupon, SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
    @Override
    public Response addInvoice(Invoice invoice, SecurityContext securityContext) throws NotFoundException {
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
    public Response deleteInvoiceById(String invoiceId, SecurityContext securityContext) throws NotFoundException {
        UUID id = UUID.fromString(invoiceId);
        try {
            bc.deleteInvoiceById(id);
            return Response.ok().entity("invoice deleted").build();

        // TODO: there will be no NullPointerException - remove on a hashmap will always succeed
        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceBip21(String invoiceId, SecurityContext securityContext) throws NotFoundException {
        UUID id = UUID.fromString(invoiceId);
        try {
            String bip21 = bc.getInvoiceBip21(id);
            return Response.ok().entity(bip21).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceById(String invoiceId, SecurityContext securityContext) throws NotFoundException {
        UUID id = UUID.fromString(invoiceId);
        try {
            Invoice invoice = bc.getInvoiceById(id);
            return Response.ok().entity(invoice).build();

        } catch (NullPointerException e) { // likely no invoice found for provided invoiceID
            Error err = new Error();
            err.setMessage("no invoice found for id " + invoiceId);
            return Response.status(404).entity(err).build();
        }
    }
    @Override
    public Response getInvoiceCouponBalance(String invoiceId, String couponAddress, SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
    @Override
    public Response getInvoiceCoupons(String invoiceId, SecurityContext securityContext) throws NotFoundException {
        // do some magic!
        return Response.ok().entity(new ApiResponseMessage(ApiResponseMessage.OK, "magic!")).build();
    }
    @Override
    public Response getInvoiceTransfers(String invoiceId, SecurityContext securityContext) throws NotFoundException {
        UUID id = UUID.fromString(invoiceId);
        try {
            List<AddressValuePair> transfers = bc.getInvoiceTransfers(id);
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
