plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.grpcContext)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.bouncycastlePgp)
  implementation(Dependencies.tink)
  implementation(Dependencies.tinkAwskms)
  implementation(Dependencies.tinkGcpkms)
  implementation(Dependencies.awsS3)
  implementation(Dependencies.loggingApi)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-testing"))
}
