package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskRealServiceModule;
import misk.config.ConfigModule;
import misk.config.MiskConfig;
import misk.environment.DeploymentModule;
import misk.environment.Env;
import misk.resources.ResourceLoader;
import misk.web.MiskWebModule;
import wisp.deployment.Deployment;

public class ExemplarJavaApp {
  public static void main(String[] args) {
    Env env = new Env("development");
    Deployment deployment = new Deployment(env.getName(), false, false, false, true);
    ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
        env, ImmutableList.of(), ResourceLoader.Companion.getSYSTEM());

    new MiskApplication(
        new MiskRealServiceModule(),
        new MiskWebModule(config.web),
        new ExemplarJavaModule(),
        new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
        new DeploymentModule(deployment, env)
    ).run(args);
  }
}
