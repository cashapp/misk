plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(libs.guava)
  api(libs.javaxInject)
  api(libs.wispLease)
}
