dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.loggingApi)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
