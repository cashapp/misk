plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(project(":misk-inject"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
