plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.prometheusClient)
  implementation(Dependencies.prometheusHotspot)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
}
