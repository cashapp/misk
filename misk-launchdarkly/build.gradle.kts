dependencies {
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.launchDarkly)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-feature"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-launchdarkly-core"))
  implementation(project(":misk-service"))
  api(project(":wisp-config"))

  testImplementation(project(":misk-testing"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
