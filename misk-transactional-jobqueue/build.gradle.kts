dependencies {
  implementation(project(":misk-core"))
  implementation(project(":misk-jobqueue"))
  implementation(project(":misk-hibernate"))

  testImplementation(project(":misk-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
