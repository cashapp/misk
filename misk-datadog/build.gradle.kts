dependencies {
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.openTracingDatadog)
  implementation(project(":misk-inject"))
  api(project(":wisp-logging"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
