plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.wispFeature)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)
}
