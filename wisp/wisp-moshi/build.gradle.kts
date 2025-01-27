plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
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
