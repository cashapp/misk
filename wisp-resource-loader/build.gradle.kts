dependencies {
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitEngine)
  testImplementation(Dependencies.kotlinTest)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
