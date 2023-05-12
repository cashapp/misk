plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.awsJavaSdkCore)
  api(libs.awsS3)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.tink)
  api(libs.tinkAwskms)
  api(libs.tinkGcpkms)
  api(libs.wispConfig)
  api(libs.wispDeployment)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.bouncycastle)
  implementation(libs.bouncycastlePgp)
  implementation(libs.kotlinLogging)
  implementation(libs.moshi)
  implementation(libs.okio)
  implementation(libs.wispLogging)
  implementation(project(":misk"))
}
