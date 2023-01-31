package com.squareup.exemplar;

import com.google.common.collect.ImmutableList;
import misk.MiskApplication;
import misk.MiskRealServiceModule;
import misk.config.ConfigModule;
import misk.environment.DeploymentModule;
import misk.resources.ResourceLoader;
import misk.web.MiskWebModule;
import wisp.config.ConfigSource;
import wisp.config.WispConfig;
import wisp.deployment.Deployment;

public class ExemplarJavaApp {
  //public static void main(String[] args) {
  //
  //  //val deployment = Deployment(name = "exemplar", isLocalDevelopment = true)
  //  ExemplarConfig config = new WispConfig()
  //      .builder().addWispConfigSources(
  //      ImmutableList.of(
  //          new ConfigSource("classpath:/exemplar-deployment.yaml", "yml")
  //          )
  //  ).build().loadConfigOrThrow<ExemplarConfig.class>();
  //  MiskApplication(
  //      MiskRealServiceModule(),
  //      MiskWebModule(config.web),
  //      ExemplarAccessModule(),
  //      ExemplarWebActionsModule(),
  //      ConfigModule.create("exemplar", config),
  //
  //
  //  Deployment deployment = new Deployment("development", false, false, false, true);
  //  //ExemplarJavaConfig config = MiskConfig.load(ExemplarJavaConfig.class, "exemplar",
  //  //    deployment, ImmutableList.of(), ResourceLoader.Companion.getSYSTEM());
  //
  //  new MiskApplication(
  //      new MiskRealServiceModule(),
  //      new MiskWebModule(config.web, ImmutableList.of()),
  //      new ExemplarJavaModule(),
  //      new ConfigModule<>(ExemplarJavaConfig.class, "exemplar", config),
  //      new DeploymentModule(deployment)
  //  ).run(args);
  //}
}
