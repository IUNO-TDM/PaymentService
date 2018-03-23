package io.swagger.api.factories;

import io.swagger.api.WalletApiService;
import io.swagger.api.impl.WalletApiServiceImpl;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-02-15T11:48:32.147Z")
public class WalletApiServiceFactory {
    private final static WalletApiService service = new WalletApiServiceImpl();

    public static WalletApiService getWalletApi() {
        return service;
    }
}
