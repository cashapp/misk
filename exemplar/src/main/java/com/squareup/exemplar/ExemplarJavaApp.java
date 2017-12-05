package com.squareup.exemplar;

import misk.MiskApplication;
import misk.MiskModule;
import misk.hibernate.HibernateModule;
import misk.moshi.MoshiModule;
import misk.web.WebActionsModule;
import misk.web.jetty.JettyModule;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    new MiskApplication(
        new MiskModule(),
        new WebActionsModule(),

        new JettyModule(),

        new HibernateModule(),

        new ExemplarJavaModule(),
        new ExemplarJavaConfigModule(),
        new MoshiModule()
    ).startAndAwaitStopped();
  }
}
