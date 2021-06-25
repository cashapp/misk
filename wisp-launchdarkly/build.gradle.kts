dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.launchDarkly)
  implementation(Dependencies.moshiKotlin)
  implementation(project(":wisp-client"))
  implementation(project(":wisp-feature"))
  implementation(project(":wisp-logging"))
  implementation(project(":wisp-ssl"))
  api(project(":wisp-config"))

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
