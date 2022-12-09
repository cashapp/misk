plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-inject"))
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}
