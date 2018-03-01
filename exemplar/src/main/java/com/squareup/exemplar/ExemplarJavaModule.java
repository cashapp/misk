package com.squareup.exemplar;

import com.google.inject.AbstractModule;
import misk.metrics.web.MetricsJsonAction;
import misk.web.WebActionModule;
import misk.web.actions.DefaultActionsModule;
import misk.web.actions.InternalErrorAction;
import misk.web.actions.LivenessCheckAction;
import misk.web.actions.NotFoundAction;
import misk.web.actions.ReadinessCheckAction;

public class ExemplarJavaModule extends AbstractModule {
  @Override protected void configure() {
    install(WebActionModule.create(HelloJavaAction.class));
    install(new DefaultActionsModule());
  }
}
