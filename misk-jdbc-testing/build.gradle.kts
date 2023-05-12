plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.datasourceProxy)
  api(libs.javaxInject)
  api(libs.moshi)
  api(libs.okHttp)
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  implementation(libs.guice)
  implementation(libs.hikariCp)
  implementation(libs.kotlinLogging)
  implementation(libs.okio)
  implementation(libs.wispContainersTesting)
  implementation(libs.wispDeployment)
  implementation(libs.wispLogging)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
  runtimeOnly(libs.hsqldb)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.wispConfig)
  testImplementation(project(":misk-config"))
  testImplementation(project(":misk-testing"))
}
