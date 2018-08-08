package io.swagger.api.impl;

import io.swagger.api.NotFoundException;
import io.swagger.api.WalletApiService;
import io.swagger.model.Balance;
import iuno.tdm.paymentservice.Bitcoin;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2018-07-25T13:00:10.686Z")
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
