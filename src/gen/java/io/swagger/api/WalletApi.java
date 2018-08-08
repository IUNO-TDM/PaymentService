package io.swagger.api;

import io.swagger.model.*;
import io.swagger.api.WalletApiService;
import io.swagger.api.factories.WalletApiServiceFactory;

import io.swagger.annotations.ApiParam;
import io.swagger.jaxrs.*;

import io.swagger.model.Balance;
import io.swagger.model.Error;

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

@Path("/wallet")
@Consumes({ "application/json" })
@Produces({ "application/json", "text/plain" })
@io.swagger.annotations.Api(description = "the wallet API")
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-25T13:00:10.686Z")
public class WalletApi  {
   private final WalletApiService delegate;

   public WalletApi(@Context ServletConfig servletContext) {
      WalletApiService delegate = null;

      if (servletContext != null) {
         String implClass = servletContext.getInitParameter("WalletApi.implementation");
         if (implClass != null && !"".equals(implClass.trim())) {
            try {
               delegate = (WalletApiService) Class.forName(implClass).newInstance();
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         } 
      }

      if (delegate == null) {
         delegate = WalletApiServiceFactory.getWalletApi();
      }

      this.delegate = delegate;
   }

    @GET
    @Path("/balance")
    @Consumes({ "application/json" })
    @Produces({ "application/json", "text/plain" })
    @io.swagger.annotations.ApiOperation(value = "Returns the balance of the internal Wallet", notes = "", response = Balance.class, tags={  })
    @io.swagger.annotations.ApiResponses(value = { 
        @io.swagger.annotations.ApiResponse(code = 200, message = "Balance with all UTXO and confident balance", response = Balance.class),
        
        @io.swagger.annotations.ApiResponse(code = 503, message = "service unavailable", response = Error.class) })
    public Response getWalletBalance(@Context SecurityContext securityContext)
    throws NotFoundException {
        return delegate.getWalletBalance(securityContext);
    }
}
