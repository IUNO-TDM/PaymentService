/*
 * IUNO TDM Payment Service API
 * Create invoices, check payment and forward coins.
 *
 * OpenAPI spec version: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */


package io.swagger.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.model.InlineResponse200;
import javax.validation.constraints.*;

/**
 * InlineResponse2001
 */
@javax.annotation.Generated(value = "io.swagger.codegen.languages.JavaJerseyServerCodegen", date = "2017-09-07T15:17:02.504Z")
public class InlineResponse2001   {
  @JsonProperty("transactionId")
  private String transactionId = null;

  @JsonProperty("state")
  private InlineResponse200 state = null;

  public InlineResponse2001 transactionId(String transactionId) {
    this.transactionId = transactionId;
    return this;
  }

  /**
   * txid
   * @return transactionId
   **/
  @JsonProperty("transactionId")
  @ApiModelProperty(value = "txid")
  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public InlineResponse2001 state(InlineResponse200 state) {
    this.state = state;
    return this;
  }

  /**
   * Get state
   * @return state
   **/
  @JsonProperty("state")
  @ApiModelProperty(value = "")
  public InlineResponse200 getState() {
    return state;
  }

  public void setState(InlineResponse200 state) {
    this.state = state;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InlineResponse2001 inlineResponse2001 = (InlineResponse2001) o;
    return Objects.equals(this.transactionId, inlineResponse2001.transactionId) &&
        Objects.equals(this.state, inlineResponse2001.state);
  }

  @Override
  public int hashCode() {
    return Objects.hash(transactionId, state);
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class InlineResponse2001 {\n");
    
    sb.append("    transactionId: ").append(toIndentedString(transactionId)).append("\n");
    sb.append("    state: ").append(toIndentedString(state)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

