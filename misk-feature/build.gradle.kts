plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.wispFeature)
  implementation(libs.guava)
  implementation(libs.kotlinStdLibJdk8)
}
