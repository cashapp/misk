plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.jakartaInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.guava)
  testImplementation(Dependencies.guice) {
    version {
      require("7.0.0")
    }
  }
  testImplementation(Dependencies.guice7Bom)
  testImplementation(Dependencies.okHttp)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-actions"))
  testImplementation(project(":misk-api"))
  testImplementation(project(":misk-inject"))
  testImplementation(project(":misk-testing"))
}
