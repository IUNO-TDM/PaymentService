package io.swagger.api;

import io.swagger.jaxrs.config.SwaggerContextService;
import io.swagger.models.Contact;
import io.swagger.models.Info;
import io.swagger.models.License;
import io.swagger.models.Swagger;
import iuno.tdm.paymentservice.Bitcoin;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.util.Enumeration;
import java.util.HashMap;

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

    HashMap<String, String> params = new HashMap<>();
    //Build parameter Hashmap
    final Enumeration initParameterNames = config.getInitParameterNames();
    while(initParameterNames.hasMoreElements()){
      Object key = initParameterNames.nextElement();

      if(key instanceof String){
        params.put((String)key,config.getInitParameter((String)key));
      }
    }

    Bitcoin bitcoin = Bitcoin.getInstance();
    bitcoin.addParams(params);
    bitcoin.start(context);

    new SwaggerContextService().withServletConfig(config).updateSwagger(swagger);
  }

  @Override
  public void destroy() {
    Bitcoin.getInstance().stop();
  }
}
