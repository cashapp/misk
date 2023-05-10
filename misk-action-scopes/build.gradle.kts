plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.moshi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}
