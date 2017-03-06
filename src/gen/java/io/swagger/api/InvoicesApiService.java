package io.swagger.api;

import io.swagger.api.*;
import io.swagger.model.*;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Coupon;
import io.swagger.model.Error;
import io.swagger.model.Invoice;
import io.swagger.model.InvoiceId;
import io.swagger.model.State;

import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.validation.constraints.*;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-03-06T12:55:18.855Z")
public abstract class InvoicesApiService {
    public abstract Response addCouponToInvoice(String invoiceId,Coupon coupon,SecurityContext securityContext) throws NotFoundException;
    public abstract Response addInvoice(Invoice invoice,SecurityContext securityContext) throws NotFoundException;
    public abstract Response deleteInvoiceById(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceBip21(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceById(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceCouponBalance(String invoiceId,String couponAddress,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceCoupons(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceState(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceTransfers(String invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoices(SecurityContext securityContext) throws NotFoundException;
}
