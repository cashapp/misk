plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.prometheusClient)
  api(Dependencies.wispConfig)
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.prometheusHotspot)
  implementation(Dependencies.prometheusHttpserver)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk-service"))
}
