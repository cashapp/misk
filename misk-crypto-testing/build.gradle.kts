plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-crypto"))
  api(project(":misk-inject"))
  implementation(libs.bouncycastle)
  implementation(libs.guice)
  implementation(libs.tink)
  implementation(libs.tinkAwskms)
  implementation(libs.tinkGcpkms)
  implementation(libs.wispDeployment)
  implementation(project(":misk-config"))

  testImplementation(libs.assertj)
  testImplementation(libs.bouncycastlePgp)
  testImplementation(libs.javaxInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.moshi)
  testImplementation(libs.okio)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
}
