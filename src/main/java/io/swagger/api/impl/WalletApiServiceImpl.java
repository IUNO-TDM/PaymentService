package io.swagger.api.impl;

import io.swagger.api.*;

import io.swagger.model.Balance;

import io.swagger.api.NotFoundException;

import iuno.tdm.paymentservice.Bitcoin;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-02-15T07:22:12.252Z")
public class WalletApiServiceImpl extends WalletApiService {
    @Override
    public Response getWalletBalance(SecurityContext securityContext) throws NotFoundException {
        Response resp;
        long spendable = Bitcoin.getInstance().getSpendableBalance();
        long estimated = Bitcoin.getInstance().getEstimatedBalance();
        Balance balance = new Balance().spendable(spendable).estimated(estimated);
        resp = Response.ok().entity(balance).build();
        return resp;
    }
}
