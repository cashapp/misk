plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-crypto"))
  api(project(":misk-inject"))
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.guice)
  implementation(Dependencies.tink)
  implementation(Dependencies.tinkAwskms)
  implementation(Dependencies.tinkGcpkms)
  implementation(Dependencies.wispDeployment)
  implementation(project(":misk-config"))

  testImplementation("com.squareup.okio:okio:3.0.0")
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.bouncycastlePgp)
  testImplementation(Dependencies.javaxInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.moshi)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
}
