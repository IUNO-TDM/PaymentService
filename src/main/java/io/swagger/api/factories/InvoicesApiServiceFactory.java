package io.swagger.api.factories;

import io.swagger.api.InvoicesApiService;
import io.swagger.api.impl.InvoicesApiServiceImpl;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-04-28T09:16:10.842Z")
public class InvoicesApiServiceFactory {
    private final static InvoicesApiService service = new InvoicesApiServiceImpl();

    public static InvoicesApiService getInvoicesApi() {
        return service;
    }
}
