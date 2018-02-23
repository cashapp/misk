package com.squareup.exemplar;

import misk.MiskApplication;
import misk.MiskModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.Environment;
import misk.environment.EnvironmentModule;
import misk.hibernate.HibernateModule;
import misk.moshi.MoshiModule;
import misk.web.WebModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Environment environment = Environment.fromEnvironmentVariable();
    ExemplarConfig config = MiskConfig.load(ExemplarConfig.class, "exemplar", environment);

    new MiskApplication(
        new MiskModule(),
        new WebModule(),
        new HibernateModule(),
        new ExemplarJavaModule(),
        new ConfigModule(ExemplarJavaConfig.class, "exemplar", config),
        new EnvironmentModule(environment)
    ).run(args);
  }
}
