package com.squareup.library;

import com.google.inject.AbstractModule;
import misk.web.WebActionModule;

public class ExemplarJavaModule extends AbstractModule {
  @Override protected void configure() {
    // TODO how to handle deprecating WebActionModule
    install(WebActionModule.create(HelloJavaAction.class));
  }
}
