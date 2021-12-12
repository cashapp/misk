plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.aws2Regions)
  api(project(":wisp-deployment"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":wisp-deployment-testing"))

}
