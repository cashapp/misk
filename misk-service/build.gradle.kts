dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.loggingApi)
  implementation(project(":misk-inject"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
