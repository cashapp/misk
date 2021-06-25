dependencies {
  implementation(Dependencies.tracingDatadog)
  implementation(Dependencies.openTracingDatadog)
  implementation(project(":misk-inject"))
  api(project(":wisp-logging"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
