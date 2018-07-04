package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.Environment;
import misk.environment.EnvironmentModule;
import misk.web.WebModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Environment environment = Environment.fromEnvironmentVariable();
    ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
        environment, ImmutableList.of());

    new MiskApplication(
        new MiskModule(),
        new WebModule(),
        new ExemplarJavaModule(),
        new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
        new EnvironmentModule(environment, null)
    ).run(args);
  }
}
