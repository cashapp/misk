package com.squareup.exemplar;

import javax.inject.Singleton;
import misk.web.Get;
import misk.web.ConcurrencyLimitsOptOut;
import misk.web.PathParam;
import misk.web.RequestHeaders;
import misk.web.ResponseContentType;
import misk.web.actions.WebAction;
import misk.web.mediatype.MediaTypes;
import okhttp3.Headers;

@Singleton
public class HelloJavaAction implements WebAction {
  @Get(pathPattern = "/hello/java/{name}")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  public HelloJavaResponse hello(@PathParam("name") String name, @RequestHeaders Headers headers) {
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
