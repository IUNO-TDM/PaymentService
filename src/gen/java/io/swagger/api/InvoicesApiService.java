package io.swagger.api;

import io.swagger.api.*;
import io.swagger.model.*;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import io.swagger.model.AddressValuePair;
import io.swagger.model.Coupon;
import io.swagger.model.Error;
import io.swagger.model.Invoice;
import io.swagger.model.State;
import io.swagger.model.Transactions;
import java.util.UUID;

import java.util.List;
import io.swagger.api.NotFoundException;

import java.io.InputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.validation.constraints.*;
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-25T13:00:10.686Z")
public abstract class InvoicesApiService {
    public abstract Response addCouponToInvoice(UUID invoiceId,Coupon coupon,SecurityContext securityContext) throws NotFoundException;
    public abstract Response addInvoice(Invoice invoice,SecurityContext securityContext) throws NotFoundException;
    public abstract Response deleteInvoiceById(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceBip21(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceById(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceCouponBalance(UUID invoiceId,String couponAddress,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceCoupons(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoicePayingTransactions(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceState(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceTransferState(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceTransferTransactions(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoiceTransfers(UUID invoiceId,SecurityContext securityContext) throws NotFoundException;
    public abstract Response getInvoices(SecurityContext securityContext) throws NotFoundException;
}
