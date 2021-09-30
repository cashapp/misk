dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.kubernetesClient)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
