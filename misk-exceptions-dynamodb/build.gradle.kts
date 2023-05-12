plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.awsJavaSdkCore)
  api(libs.javaxInject)
  api(libs.slf4jApi)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.okHttp)
  implementation(project(":misk-core"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}
