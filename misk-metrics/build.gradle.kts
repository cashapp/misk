plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.prometheusClient)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.prometheusHotspot)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
}
