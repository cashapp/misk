plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-testing"))
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jedis)
  implementation(Dependencies.okio)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-redis"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wispTimeTesting)
}
