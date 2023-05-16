plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.jedis)
  api(Dependencies.wispConfig)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.apacheCommonsPool2)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.prometheusClient)
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-redis"))
  testImplementation(project(":misk-redis-testing"))
  testImplementation(project(":misk-testing"))
}
