package io.swagger.api.factories;

import io.swagger.api.PaymentChannelsApiService;
import io.swagger.api.impl.PaymentChannelsApiServiceImpl;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-07-18T06:51:44.758Z")
public class PaymentChannelsApiServiceFactory {
    private final static PaymentChannelsApiService service = new PaymentChannelsApiServiceImpl();

    public static PaymentChannelsApiService getPaymentChannelsApi() {
        return service;
    }
}
