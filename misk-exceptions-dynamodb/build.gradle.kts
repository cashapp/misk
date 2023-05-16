plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.awsDynamodb)
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.javaxInject)
  api(Dependencies.slf4jApi)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(project(":misk-core"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-testing"))
}
