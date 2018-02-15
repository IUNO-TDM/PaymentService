package io.swagger.api.factories;

import io.swagger.api.InvoicesApiService;
import io.swagger.api.impl.InvoicesApiServiceImpl;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-02-15T11:48:32.147Z")
public class InvoicesApiServiceFactory {
    private final static InvoicesApiService service = new InvoicesApiServiceImpl();

    public static InvoicesApiService getInvoicesApi() {
        return service;
    }
}
