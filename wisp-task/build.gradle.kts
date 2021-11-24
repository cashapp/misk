dependencies {
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.kotlinxCoroutinesCoreJvm)
  implementation(Dependencies.kotlinStdLibJdk8)
  api(Dependencies.kotlinRetry)
  implementation(Dependencies.micrometerPrometheus)
  implementation(project(":wisp-config"))
  implementation(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
