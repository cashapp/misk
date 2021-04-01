dependencies {
  api(Dependencies.guice)
  api(Dependencies.guiceMultibindings)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.assertj)
  implementation(Dependencies.kotlinTest)

  testImplementation(project(":misk-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
