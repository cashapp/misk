plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.wispConfig)

  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-metrics-testing"))
  testImplementation(project(":misk-redis-testing"))
  testImplementation(project(":misk-testing"))
}
