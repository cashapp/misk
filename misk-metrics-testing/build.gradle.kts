plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.prometheusClient)
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
}
