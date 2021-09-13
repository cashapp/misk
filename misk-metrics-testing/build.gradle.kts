dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(project(":misk-inject"))
  implementation(project(":misk-metrics"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.junitParams)
  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
