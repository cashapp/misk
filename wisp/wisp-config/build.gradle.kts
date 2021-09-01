dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  api(Dependencies.hopliteCore)
  api(Dependencies.hopliteHocon)
  api(Dependencies.hopliteJson)
  api(Dependencies.hopliteToml)
  api(Dependencies.hopliteYaml)
  api(project(":wisp-resource-loader"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
