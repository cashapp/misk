plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.cronUtils)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.moshiAdapters)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(Dependencies.wispLease)
  api(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-testing"))
}
