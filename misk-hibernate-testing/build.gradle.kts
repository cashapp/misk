dependencies {
  implementation(Dependencies.guice)
  api(project(":misk-jdbc-testing"))
  api(project(":misk"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-jdbc"))
  api(project(":misk-testing"))
  api(project(":misk-hibernate"))
  testImplementation(Dependencies.junitApi)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
