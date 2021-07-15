dependencies {
  api(Dependencies.okHttp)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
