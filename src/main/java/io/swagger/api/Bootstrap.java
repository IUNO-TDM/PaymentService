package io.swagger.api;

import io.swagger.jaxrs.config.SwaggerContextService;
import io.swagger.models.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import iuno.tdm.paymentservice.Bitcoin;

public class Bootstrap extends HttpServlet {
  @Override
  public void init(ServletConfig config) throws ServletException {
    Info info = new Info()
      .title("Swagger Server")
      .description("Create invoices, check payment and forward coins.")
      .termsOfService("")
      .contact(new Contact()
        .email(""))
      .license(new License()
        .name("")
        .url("http://unlicense.org"));

    ServletContext context = config.getServletContext();
    Swagger swagger = new Swagger().info(info);

    Bitcoin bitcoin = Bitcoin.getInstance();
    bitcoin.start();

    new SwaggerContextService().withServletConfig(config).updateSwagger(swagger);
  }

  @Override
  public void destroy() {
    Bitcoin.getInstance().stop();
  }
}
