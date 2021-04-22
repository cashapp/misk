dependencies {
  api(project(":wisp-config"))
  api(project(":wisp-logging"))
  api(project(":wisp-resource-loader"))
  api(project(":wisp-ssl"))
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  api(Dependencies.kotlinRetry)
  api(Dependencies.loggingApi)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.okio)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.slf4jApi)

  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp-logging-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
