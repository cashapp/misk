dependencies {
  implementation(Dependencies.docker)
  // The docker-java we use in tests depends on an old version of junixsocket that depends on
  // log4j. We force it up a minor version in packages that use it.
  implementation("com.kohlschutter.junixsocket:junixsocket-native-common:2.3.4") {
    isForce = true
  }
  implementation("com.kohlschutter.junixsocket:junixsocket-common:2.3.4") {
    isForce = true
  }
  implementation(Dependencies.guice)
  implementation(Dependencies.junitApi)
  implementation(Dependencies.junitParams)
  implementation(Dependencies.junitEngine)
  implementation(Dependencies.assertj)
  implementation(Dependencies.kotlinTest)
  api(Dependencies.loggingApi)
  api(Dependencies.logbackClassic)
  implementation(Dependencies.okHttpMockWebServer) {
    exclude(group = "junit")
  }
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracingMock)
  implementation(Dependencies.mockitoCore)
  implementation(Dependencies.guavaTestLib) {
    exclude(group = "junit")
  }
  implementation(Dependencies.javaxInject)
  api(project(":wisp-containers-testing"))
  api(project(":wisp-logging"))
  api(project(":wisp-logging-testing"))
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-service"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
