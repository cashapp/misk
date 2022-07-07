plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.loggingApi)
  api(Dependencies.wispFeature)
}
