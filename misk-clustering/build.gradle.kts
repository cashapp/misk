plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kubernetesClient) {
    // We don't use the prometheus-related code in the kubernetes client, and
    // if we don't exclude it, it drags in a version of prometheus that's 0.10.0+
    // which we don't want, because prometheus 0.10+ enforce _total in counters.
    // This is a breaking change that we should deal with, but haven't done so yet.
    // See: https://github.com/prometheus/client_java/releases/tag/parent-0.10.0
    exclude(group = "io.prometheus")
  }
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
