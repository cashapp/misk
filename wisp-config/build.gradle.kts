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

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
