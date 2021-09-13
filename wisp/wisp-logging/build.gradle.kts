dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  api(Dependencies.loggingApi)
  implementation(Dependencies.logbackClassic)
  implementation(Dependencies.slf4jApi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(project(":wisp-logging-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
