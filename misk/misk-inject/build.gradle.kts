plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-testing"))
}
