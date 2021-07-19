dependencies {
  api(Dependencies.okHttp)
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
