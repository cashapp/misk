dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  api(project(":misk"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":misk-testing"))
  api(project(":misk-hibernate"))
  api(project(":misk-jobqueue"))
  api(project(":misk-transactional-jobqueue"))

  testImplementation(project(":wisp-config"))
  testImplementation(project(":misk-hibernate"))
  testImplementation(project(":misk-hibernate-testing"))
  testImplementation(project(":wisp-logging"))
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.loggingApi)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
