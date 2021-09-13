dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.guava)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.loggingApi)
  api(project(":wisp-feature"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
