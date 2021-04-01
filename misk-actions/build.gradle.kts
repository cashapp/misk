dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
