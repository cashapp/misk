package com.squareup.exemplar;

import com.google.inject.AbstractModule;
import misk.config.ConfigModule;

public class ExemplarJavaConfigModule extends AbstractModule {
  private final ExemplarConfig config;

  ExemplarJavaConfigModule(ExemplarConfig config) {
    this.config = config;
  }

  @Override protected void configure() {
    install(new ConfigModule(ExemplarJavaConfig.class, "exemplar", config));
  }
}
