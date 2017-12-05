package com.squareup.exemplar;

import com.google.inject.AbstractModule;
import misk.config.ConfigModule;

public class ExemplarJavaConfigModule extends AbstractModule {
  @Override protected void configure() {
    install(new ConfigModule(ExemplarJavaConfig.class, "exemplar.yaml"));
  }
}
