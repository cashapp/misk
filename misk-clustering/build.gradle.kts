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
  implementation(Dependencies.wispConfig)
  implementation(Dependencies.wispLease)
  // TODO (rmariano): misk-clustering-testing?
  implementation(Dependencies.wispLeaseTesting)
  implementation(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(project(":misk-testing"))
}
