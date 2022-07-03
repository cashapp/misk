plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.datasourceProxy)
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.hsqldb)
  implementation(Dependencies.mysql)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.openTracingJdbc)
  implementation(Dependencies.postgresql)
  implementation(Dependencies.vitess) {
    exclude("org.apache.logging.log4j")
  }
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.prometheusClient)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispLogging)
  api(Dependencies.wispMoshi)

  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-jdbc-testing"))
  testImplementation(Dependencies.tracingDatadog)
  testImplementation(Dependencies.openTracingDatadog)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.openTracingMock)
}
