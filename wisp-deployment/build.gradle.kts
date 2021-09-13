dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(project(":wisp-deployment-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
