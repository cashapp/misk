dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.awsDynamodb)
  implementation(project(":misk-aws"))
  implementation(project(":misk-inject"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
