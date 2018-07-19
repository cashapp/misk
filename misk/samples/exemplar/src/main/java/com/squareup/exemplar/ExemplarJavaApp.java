package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskServiceModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.Environment;
import misk.environment.EnvironmentModule;
import misk.web.MiskWebModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Environment environment = Environment.fromEnvironmentVariable();
    ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
        environment, ImmutableList.of());

    new MiskApplication(
        new MiskServiceModule(),
        new MiskWebModule(),
        new ExemplarJavaModule(),
        new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
        new EnvironmentModule(environment, null)
    ).run(args);
  }
}
