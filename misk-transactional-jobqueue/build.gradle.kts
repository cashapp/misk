dependencies {
  implementation(project(":misk-core"))
  implementation(project(":misk-jobqueue"))
  implementation(project(":misk-hibernate"))

  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
