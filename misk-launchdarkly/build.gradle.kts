plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guice)
  api(libs.launchDarkly)
  api(libs.wispConfig)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.javaxInject)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.moshi)
  implementation(libs.wispFeature)
  implementation(libs.wispLaunchDarkly)
  implementation(libs.wispSsl)
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
}
