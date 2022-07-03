plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.launchDarkly)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
  api(Dependencies.wispConfig)

  testImplementation(project(":misk-testing"))
}
