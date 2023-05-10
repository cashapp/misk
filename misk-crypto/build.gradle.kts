plugins {
  kotlin("jvm")
  `java-library`
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
}
