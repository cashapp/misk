package com.squareup.exemplar;

import misk.web.Get;
import misk.web.JsonResponseBody;
import misk.web.PathParam;
import misk.web.RequestHeaders;
import misk.web.actions.WebAction;
import javax.inject.Singleton;
import okhttp3.Headers;

@Singleton
public class HelloJavaAction implements WebAction {
  @Get(pathPattern = "/hello/java/{name}")
  @JsonResponseBody
  public HelloJavaResponse hello(
      @PathParam("name") String name,
      @RequestHeaders Headers headers) {
    return new HelloJavaResponse("YO", name.toUpperCase());
  }

  static class HelloJavaResponse {
    private final String greeting;
    private final String name;

    HelloJavaResponse(String greeting, String name) {
      this.greeting = greeting;
      this.name = name;
    }
  }
}
