package com.squareup.exemplar;

import misk.MiskApplication;
import misk.MiskModule;
import misk.environment.EnvironmentModule;
import misk.hibernate.HibernateModule;
import misk.web.WebModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    new MiskApplication(
        new MiskModule(),

        new WebModule(),

        new HibernateModule(),

        new ExemplarJavaModule(),
        new ExemplarJavaConfigModule(),
        EnvironmentModule.fromEnvironmentVariable()
    ).run(args);
  }
}
