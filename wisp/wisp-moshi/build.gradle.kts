plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.protobuf)
  alias(libs.plugins.mavenPublish)
}

dependencies {
  api(libs.moshiCore)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.javaxInject)
  implementation(libs.moshiKotlin)

  testRuntimeOnly(libs.junitEngine)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}
