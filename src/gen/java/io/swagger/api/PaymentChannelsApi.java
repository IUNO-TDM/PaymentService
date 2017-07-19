package io.swagger.api;

import io.swagger.model.*;
import io.swagger.api.PaymentChannelsApiService;
import io.swagger.api.factories.PaymentChannelsApiServiceFactory;

import io.swagger.annotations.ApiParam;
import io.swagger.jaxrs.*;

import io.swagger.model.PaymentChannel;

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

@Path("/paymentChannels")
@Consumes({ "application/json" })
@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the paymentChannels API")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-07-18T06:51:44.758Z")
public class PaymentChannelsApi  {
   private final PaymentChannelsApiService delegate;

   public PaymentChannelsApi(@Context ServletConfig servletContext) {
      PaymentChannelsApiService delegate = null;

      if (servletContext != null) {
         String implClass = servletContext.getInitParameter("PaymentChannelsApi.implementation");
         if (implClass != null && !"".equals(implClass.trim())) {
            try {
               delegate = (PaymentChannelsApiService) Class.forName(implClass).newInstance();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         } 
      }

      if (delegate == null) {
         delegate = PaymentChannelsApiServiceFactory.getPaymentChannelsApi();
      }

      this.delegate = delegate;
   }

    @GET
    
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns all known paymentChannels", notes = "", response = PaymentChannel.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "OK", response = PaymentChannel.class, responseContainer = "List") })
    public Response getPaymentChannels(@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getPaymentChannels(securityContext);
    }
    @GET
    @Path("/{pubKey}")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns all known paymentChannels filtered by pubKey", notes = "", response = PaymentChannel.class, responseContainer = "List", tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "OK", response = PaymentChannel.class, responseContainer = "List") })
    public Response getPaymentChannelsByPubkey(@ApiParam(value = "the id of the invoice to get the coupons balance for",required=true) @PathParam("pubKey") String pubKey
,@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getPaymentChannelsByPubkey(pubKey,securityContext);
    }
}
