dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  api(project(":wisp-deployment"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
