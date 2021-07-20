dependencies {
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(project(":misk-core"))

  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
