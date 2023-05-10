plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.assertj)
  api(Dependencies.dockerApi)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.jettyServletApi)
  api(Dependencies.junitApi)
  api(Dependencies.kotlinLogging)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.openTracingMock)
  api(Dependencies.servletApi)
  api(Dependencies.wispTimeTesting)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  implementation(Dependencies.guavaTestLib)
  implementation(Dependencies.guiceTestLib)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.mockitoCore)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracingApi)
  implementation(Dependencies.slf4jApi)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispDeployment)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispLoggingTesting)
  implementation(project(":misk-action-scopes"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.kotlinTest)
}
