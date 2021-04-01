dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.launchDarkly)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.moshiCore)
  implementation(project(":misk-feature"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(Dependencies.moshiKotlin)
  testImplementation(Dependencies.moshiAdapters)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
