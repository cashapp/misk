dependencies {
  implementation(Dependencies.guice)
  api(project(":misk-events-core"))
  implementation(Dependencies.javaxInject)
  api(project(":misk-inject"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.okio)
  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
