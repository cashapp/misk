plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.javaxInject)
  api(libs.kotlinLogging)
  api(libs.wispConfig)
  api(libs.wispLeaseTesting)
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.kubernetesClient)
  implementation(libs.kubernetesClientApi)
  implementation(libs.okHttp)
  implementation(libs.wispLease)
  implementation(libs.wispLogging)
  implementation(project(":misk-core"))
  implementation(project(":misk-lease"))
  implementation(project(":misk-service"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}
