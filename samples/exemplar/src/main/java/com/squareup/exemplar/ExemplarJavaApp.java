package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskRealServiceModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.DeploymentModule;
import misk.resources.ResourceLoader;
import misk.web.MiskWebModule;
import wisp.deployment.Deployment;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Deployment deployment = new Deployment("development", false, false, false, true);
    ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
        deployment, ImmutableList.of(), ResourceLoader.Companion.getSYSTEM());

    new MiskApplication(
        new MiskRealServiceModule(),
        new MiskWebModule(config.web, ImmutableList.of()),
        new ExemplarJavaModule(),
        new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
        new DeploymentModule(deployment)
    ).run(args);
  }
}
