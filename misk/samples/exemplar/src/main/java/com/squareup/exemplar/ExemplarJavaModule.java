package com.squareup.exemplar;

import com.google.inject.AbstractModule;
import misk.web.WebActionModule;

public class ExemplarJavaModule extends AbstractModule {
  @Override protected void configure() {
    install(WebActionModule.create(HelloJavaAction.class));
  }
}
