plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  api(project(":wisp-deployment"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}
