plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish")
}

dependencies {
  api(project(":misk-testing-api"))
  api(libs.logbackClassic)
  implementation(libs.logbackCore)
  implementation(libs.slf4jApi)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(project(":wisp:wisp-logging"))
}
