dependencies {
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)
  api(project(":wisp-resource-loader"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
