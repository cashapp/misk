dependencies {
  implementation(Dependencies.grpcContext)
  implementation(Dependencies.guice)
  implementation(Dependencies.okio)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.bouncycastlePgp)
  implementation(Dependencies.tink)
  implementation(Dependencies.tinkAwskms)
  implementation(Dependencies.tinkGcpkms)
  implementation(Dependencies.awsS3)
  implementation(Dependencies.loggingApi)
  implementation(project(":misk"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  api(project(":wisp-config"))
  api(project(":wisp-deployment"))
  api(project(":wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":wisp-logging"))
  testImplementation(project(":misk-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
