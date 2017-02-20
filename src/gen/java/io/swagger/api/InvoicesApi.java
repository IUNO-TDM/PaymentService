package io.swagger.api;

import io.swagger.model.*;
import io.swagger.api.InvoicesApiService;
import io.swagger.api.factories.InvoicesApiServiceFactory;

import io.swagger.annotations.ApiParam;
import io.swagger.jaxrs.*;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Coupon;
import io.swagger.model.Error;
import io.swagger.model.Invoice;
import io.swagger.model.InvoiceId;

import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.*;

@Path("/invoices")
@Consumes({ "application/json" })
@Produces({ "application/json" })
@io.swagger.annotations.Api(description = "the invoices API")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-02-20T13:03:48.706Z")
public class InvoicesApi  {
   private final InvoicesApiService delegate = InvoicesApiServiceFactory.getInvoicesApi();

    @POST
    @Path("/{invoiceId}/coupons")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Adds one coupon to the invoice.", notes = "", response = AddressValuePair.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 201, message = "returns the balance of the new coupon", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice id not found", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 409, message = "invoice already closed", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 503, message = "balance of coupon could not be retrieved", response = AddressValuePair.class) })
    public Response addCouponToInvoice(@ApiParam(value = "the id of the invoice the coupon is for",required=true) @PathParam("invoiceId") String invoiceId
,@ApiParam(value = "coupon data" ,required=true) Coupon coupon
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.addCouponToInvoice(invoiceId,coupon,securityContext);
    }
    @POST
    
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Add one new invoice.", notes = "", response = Invoice.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 201, message = "id of new invoice", response = Invoice.class),
        
        @io.swagger.annotations.ApiResponse(code = 400, message = "bad request", response = Invoice.class),
        
        @io.swagger.annotations.ApiResponse(code = 503, message = "service unavailable", response = Invoice.class) })
    public Response addInvoice(@ApiParam(value = "one new invoice" ,required=true) Invoice invoice
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.addInvoice(invoice,securityContext);
    }
    @DELETE
    @Path("/{invoiceId}")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Deletes the invoice to the provided ID.", notes = "", response = void.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "invoice deleted", response = void.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = void.class) })
    public Response deleteInvoiceById(@ApiParam(value = "the id of the invoice to delete",required=true) @PathParam("invoiceId") String invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.deleteInvoiceById(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/bip21")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Returns a Bip21 URI for the invoice.", notes = "", response = String.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the Bip21 URI for the invoice", response = String.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = String.class) })
    public Response getInvoiceBip21(@ApiParam(value = "the invoice id to get the Bip21 URI for",required=true) @PathParam("invoiceId") String invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceBip21(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Returns information about the invoice to the provided id.", notes = "", response = Invoice.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the information about the invoice", response = Invoice.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Invoice.class) })
    public Response getInvoiceById(@ApiParam(value = "the invoice id to get the information for",required=true) @PathParam("invoiceId") String invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceById(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/coupons/{couponAddress}")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Returns the balance for the requested coupon.", notes = "", response = AddressValuePair.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the balance of the coupon", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "object not found", response = AddressValuePair.class) })
    public Response getInvoiceCouponBalance(@ApiParam(value = "the id of the invoice to get the coupons balance for",required=true) @PathParam("invoiceId") String invoiceId
,@ApiParam(value = "the address of the coupon to get the balance for",required=true) @PathParam("couponAddress") String couponAddress
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceCouponBalance(invoiceId,couponAddress,securityContext);
    }
    @GET
    @Path("/{invoiceId}/coupons")
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "Returns a list of coupon adresses along with their balance.", notes = "", response = AddressValuePair.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the balance for each coupon", response = AddressValuePair.class, responseContainer = "List"),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice id not found", response = AddressValuePair.class, responseContainer = "List") })
    public Response getInvoiceCoupons(@ApiParam(value = "the id of the invoice to get the coupons balances for",required=true) @PathParam("invoiceId") String invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceCoupons(invoiceId,securityContext);
    }
    @GET
    
    @Consumes({ "application/json" })
    @Produces({ "application/json" })
    @io.swagger.annotations.ApiOperation(value = "The invoices endpoint returns a list of all known invoices ids.", notes = "", response = InvoiceId.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "an array of invoice ids", response = InvoiceId.class, responseContainer = "List") })
    public Response getInvoices(@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoices(securityContext);
    }
}
