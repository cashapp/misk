plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.guava)
  api(Dependencies.javaxInject)
  api(Dependencies.wispLease)
}
