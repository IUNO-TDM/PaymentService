package io.swagger.api;

import io.swagger.api.*;
import io.swagger.model.*;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import io.swagger.model.PaymentChannel;

import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.validation.constraints.*;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-07-18T06:51:44.758Z")
public abstract class PaymentChannelsApiService {
    public abstract Response getPaymentChannels(SecurityContext securityContext) throws NotFoundException;
    public abstract Response getPaymentChannelsByPubkey(String pubKey,SecurityContext securityContext) throws NotFoundException;
}
