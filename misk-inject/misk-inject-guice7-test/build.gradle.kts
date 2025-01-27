plugins {
  id("org.jetbrains.kotlin.jvm")
}

dependencies {
  testImplementation(libs.assertj)
  testImplementation(libs.jakartaInject)
  testImplementation(libs.junitApi)
  testImplementation(libs.guava)
  testImplementation(libs.guice) {
    version {
      require("7.0.0")
    }
  }
  testImplementation(libs.guice7Bom)
  testImplementation(libs.okHttp)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-actions"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-testing"))
}
