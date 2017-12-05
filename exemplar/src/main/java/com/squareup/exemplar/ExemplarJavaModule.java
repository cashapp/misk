package com.squareup.exemplar;

import com.google.inject.AbstractModule;
import misk.web.WebActionModule;
import misk.web.actions.InternalErrorAction;
import misk.web.actions.NotFoundAction;

public class ExemplarJavaModule extends AbstractModule {
  @Override protected void configure() {
    install(WebActionModule.create(HelloJavaAction.class));
    install(WebActionModule.create(InternalErrorAction.class));
    install(WebActionModule.create(NotFoundAction.class));
  }
}
