plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.prometheusClient)
  api(libs.wispConfig)
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.guice)
  implementation(libs.kotlinLogging)
  implementation(libs.prometheusHotspot)
  implementation(libs.prometheusHttpserver)
  implementation(libs.wispLogging)
  implementation(project(":misk-service"))
}
