plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.jedis)
  api(project(":misk-inject"))
  api(project(":misk-redis"))
  api(project(":misk-testing"))
  implementation(libs.dockerApi)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.kotlinLogging)
  implementation(libs.okio)
  implementation(libs.wispContainersTesting)
  implementation(libs.wispLogging)

  testImplementation(libs.wispDeployment)
  testImplementation(libs.junitApi)
  testImplementation(libs.assertj)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.wispTimeTesting)
  testImplementation(project(":misk"))
}
