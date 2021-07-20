dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.datasourceProxy)
  implementation(Dependencies.docker)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.3.4") {
    isForce = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.4") {
    isForce = true
  }
  implementation(Dependencies.hikariCp)
  implementation(Dependencies.hsqldb)
  implementation(Dependencies.mysql)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.openTracingJdbc)
  implementation(Dependencies.postgresql)
  implementation(Dependencies.vitess)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.prometheusClient)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":wisp-config"))
  api(project(":wisp-deployment"))
  api(project(":wisp-logging"))

  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-jdbc-testing"))
  testImplementation(Dependencies.tracingDatadog)
  testImplementation(Dependencies.openTracingDatadog)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.openTracingMock)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
