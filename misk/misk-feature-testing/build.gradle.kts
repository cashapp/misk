plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.wispFeature)
  api(Dependencies.wispFeatureTesting)
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshi)
  implementation(project(":misk-service"))
}
