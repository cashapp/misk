plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kubernetesClient)
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  implementation(project(":wisp-config"))
  implementation(project(":wisp-lease"))
  // TODO (rmariano): misk-clustering-testing?
  implementation(project(":wisp-lease-testing"))
  implementation(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(project(":misk-testing"))
}
