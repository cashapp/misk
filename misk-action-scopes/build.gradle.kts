plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}
