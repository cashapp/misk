plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.jedis)
  api(libs.wispConfig)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.apacheCommonsPool2)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.okio)
  implementation(libs.prometheusClient)
  implementation(libs.wispDeployment)
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-redis-testing"))
  testImplementation(project(":misk-testing"))
}
