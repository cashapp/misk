plugins {
  kotlin("jvm")
  `java-library`
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.awsS3)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tink)
  api(Dependencies.tinkAwskms)
  api(Dependencies.tinkGcpkms)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.bouncycastlePgp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.moshi)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk"))

  testImplementation("com.squareup.okio:okio:3.0.0")
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-testing"))

  testFixturesApi(project(":misk-crypto"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(Dependencies.bouncycastle)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.tink)
  testFixturesImplementation(Dependencies.tinkAwskms)
  testFixturesImplementation(Dependencies.tinkGcpkms)
  testFixturesImplementation(Dependencies.wispDeployment)
  testFixturesImplementation(project(":misk-config"))
}
