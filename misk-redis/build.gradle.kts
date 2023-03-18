plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(Dependencies.wispConfig)

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-redis-testing"))
  testImplementation(project(":misk-testing"))
}
