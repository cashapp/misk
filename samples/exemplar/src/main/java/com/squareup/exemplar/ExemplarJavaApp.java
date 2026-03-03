package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskRealServiceModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.Env;
import misk.environment.Environment;
import misk.environment.EnvironmentModule;
import misk.resources.ResourceLoader;
import misk.web.MiskWebModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Environment environment = Environment.fromEnvironmentVariable();
    ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
        new Env(environment.name()), ImmutableList.of(), ResourceLoader.Companion.getSYSTEM());

    new MiskApplication(
        new MiskRealServiceModule(),
        new MiskWebModule(config.web),
        new ExemplarJavaModule(),
        new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
        new EnvironmentModule(environment)
    ).run(args);
  }
}
