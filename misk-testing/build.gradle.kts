plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.guice)
  implementation(Dependencies.guiceTestLib)
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
