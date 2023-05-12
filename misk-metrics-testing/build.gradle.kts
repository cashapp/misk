plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.prometheusClient)
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}
