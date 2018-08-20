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
import io.swagger.model.State;
import io.swagger.model.Transactions;
import java.util.UUID;

import java.util.Map;
import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.servlet.ServletConfig;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.*;
import javax.validation.constraints.*;

@Path("/invoices")
@Consumes({ "application/json" })
@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the invoices API")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-25T13:00:10.686Z")
public class InvoicesApi  {
   private final InvoicesApiService delegate;

   public InvoicesApi(@Context ServletConfig servletContext) {
      InvoicesApiService delegate = null;

      if (servletContext != null) {
         String implClass = servletContext.getInitParameter("InvoicesApi.implementation");
         if (implClass != null && !"".equals(implClass.trim())) {
            try {
               delegate = (InvoicesApiService) Class.forName(implClass).newInstance();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         } 
      }

      if (delegate == null) {
         delegate = InvoicesApiServiceFactory.getInvoicesApi();
      }

      this.delegate = delegate;
   }

    @POST
    @Path("/{invoiceId}/coupons")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Adds one coupon to the invoice.", notes = "", response = AddressValuePair.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 201, message = "returns the balance of the new coupon", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice id not found", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 409, message = "invoice already closed", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 422, message = "coupon code is invalid", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 503, message = "balance of coupon could not be retrieved", response = Error.class) })
    public Response addCouponToInvoice(@ApiParam(value = "the id of the invoice the coupon is for",required=true) @PathParam("invoiceId") UUID invoiceId
,@ApiParam(value = "coupon code" ,required=true) Coupon coupon
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.addCouponToInvoice(invoiceId,coupon,securityContext);
    }
    @POST
    
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Add one new invoice.", notes = "", response = Invoice.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 201, message = "id of new invoice", response = Invoice.class),
        
        @io.swagger.annotations.ApiResponse(code = 400, message = "bad request", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 503, message = "service unavailable", response = Error.class) })
    public Response addInvoice(@ApiParam(value = "one new invoice" ,required=true) Invoice invoice
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.addInvoice(invoice,securityContext);
    }
    @DELETE
    @Path("/{invoiceId}")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Deletes the invoice to the provided ID.", notes = "", response = Void.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "invoice deleted", response = Void.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response deleteInvoiceById(@ApiParam(value = "the id of the invoice to delete",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.deleteInvoiceById(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/bip21")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns a Bip21 URI for the invoice.", notes = "", response = String.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the Bip21 URI for the invoice", response = String.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response getInvoiceBip21(@ApiParam(value = "the invoice id to get the Bip21 URI for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceBip21(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns information about the invoice to the provided id.", notes = "", response = Invoice.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the information about the invoice", response = Invoice.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response getInvoiceById(@ApiParam(value = "the invoice id to get the information for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceById(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/coupons/{couponAddress}")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns the balance for the requested coupon.", notes = "", response = AddressValuePair.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the balance of the coupon", response = AddressValuePair.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "object not found", response = Error.class) })
    public Response getInvoiceCouponBalance(@ApiParam(value = "the id of the invoice to get the coupons balance for",required=true) @PathParam("invoiceId") UUID invoiceId
,@ApiParam(value = "the address of the coupon to get the balance for",required=true) @PathParam("couponAddress") String couponAddress
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceCouponBalance(invoiceId,couponAddress,securityContext);
    }
    @GET
    @Path("/{invoiceId}/coupons")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns a list of coupon adresses along with their balance.", notes = "", response = AddressValuePair.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the balance for each coupon", response = AddressValuePair.class, responseContainer = "List"),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice id not found", response = Error.class) })
    public Response getInvoiceCoupons(@ApiParam(value = "the id of the invoice to get the coupons balances for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceCoupons(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/payingTransactions")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns all transaction that are paying this invoice", notes = "", response = Transactions.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the state object of the transfer tx", response = Transactions.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response getInvoicePayingTransactions(@ApiParam(value = "the invoice id to get the state for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoicePayingTransactions(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/state")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns a confidence object that describes the state of the most confident incoming tx.", notes = "", response = State.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the state object of the incoming tx", response = State.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response getInvoiceState(@ApiParam(value = "the invoice id to get the state for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceState(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/transferState")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns a confidence object that describes the state of the most confident transfer tx.", notes = "", response = State.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the state object of the transfer tx", response = State.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 423, message = "no transfer state because there are no transfers in the invoice", response = Error.class) })
    public Response getInvoiceTransferState(@ApiParam(value = "the invoice id to get the state for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceTransferState(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/transferTransactions")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns all transaction that are paying transfers", notes = "", response = Transactions.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the state object of the transfer tx", response = Transactions.class),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class),
        
        @io.swagger.annotations.ApiResponse(code = 423, message = "no tx because there are no transfers in the invoice", response = Error.class) })
    public Response getInvoiceTransferTransactions(@ApiParam(value = "the invoice id to get the state for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceTransferTransactions(invoiceId,securityContext);
    }
    @GET
    @Path("/{invoiceId}/transfers")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns a transfer object as array of address/value pairs to complete the invoice in one transaction.", notes = "", response = AddressValuePair.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "returns the address value pairs for the invoice as array", response = AddressValuePair.class, responseContainer = "List"),
        
        @io.swagger.annotations.ApiResponse(code = 404, message = "invoice not found", response = Error.class) })
    public Response getInvoiceTransfers(@ApiParam(value = "the invoice id to get transfers for",required=true) @PathParam("invoiceId") UUID invoiceId
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoiceTransfers(invoiceId,securityContext);
    }
    @GET
    
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "The invoices endpoint returns a list of all known invoices ids.", notes = "", response = UUID.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "an array of invoice ids", response = UUID.class, responseContainer = "List") })
    public Response getInvoices(@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getInvoices(securityContext);
    }
}
