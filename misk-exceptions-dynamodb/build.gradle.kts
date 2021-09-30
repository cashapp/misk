dependencies {
  api(Dependencies.awsDynamodb)

  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-aws"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))

  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
}
