plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  api(Dependencies.javaxInject)
  api(Dependencies.jedis)
  api(project(":misk-inject"))
  api(project(":misk-redis"))
  api(project(":misk-testing"))
  implementation(Dependencies.dockerApi)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispContainersTesting)
  implementation(Dependencies.wispLogging)

  testImplementation(Dependencies.wispDeployment)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk"))
}
