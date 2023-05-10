plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.launchDarkly)
  api(Dependencies.wispConfig)
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshi)
  implementation(Dependencies.wispFeature)
  implementation(Dependencies.wispLaunchDarkly)
  implementation(Dependencies.wispSsl)
  implementation(project(":misk-feature"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
}
