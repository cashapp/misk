plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.assertj)
  api(libs.dockerApi)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.jettyServletApi)
  api(libs.junitApi)
  api(libs.kotlinLogging)
  api(libs.moshi)
  api(libs.okHttp)
  api(libs.openTracingMock)
  api(libs.servletApi)
  api(libs.wispTimeTesting)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(libs.guavaTestLib)
  implementation(libs.guiceTestLib)
  implementation(libs.logbackClassic)
  implementation(libs.mockitoCore)
  implementation(libs.okio)
  implementation(libs.openTracingApi)
  implementation(libs.slf4jApi)
  implementation(libs.wispContainersTesting)
  implementation(libs.wispDeployment)
  implementation(libs.wispLogging)
  implementation(libs.wispLoggingTesting)
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))

  testImplementation(libs.kotlinTest)
}
