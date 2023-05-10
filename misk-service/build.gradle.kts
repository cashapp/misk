plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":misk-testing"))
}
