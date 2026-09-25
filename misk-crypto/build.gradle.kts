import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.aws2Auth)
  api(libs.aws2S3)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.tink)
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.aws2Core)
  implementation(libs.aws2Regions)
  implementation(libs.awsSdkCore)
  implementation(libs.tinkAwskms) {
    // tink-awskms 2.0.0 declares tink 1.21.0, but only uses the KmsClient/KmsClients/Aead
    // interfaces, which are stable across tink versions. Excluding the transitive tink lets
    // consumers that strictly pin an older tink (and misk itself, on 1.12.0) resolve cleanly.
    exclude(group = "com.google.crypto.tink", module = "tink")
  }
  implementation(libs.bouncyCastleProvider)
  implementation(libs.bouncyCastlePgp)
  implementation(libs.guava)
  implementation(libs.loggingApi)
  implementation(libs.moshiCore)
  implementation(libs.okio)
  implementation(libs.tinkGcpkms)
  implementation(project(":misk-logging"))
  implementation(project(":misk-moshi"))

  runtimeOnly(libs.bouncyCastlePkix)

  testFixturesApi(project(":misk-crypto"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(libs.bouncyCastleProvider)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.tink)
  testFixturesImplementation(libs.tinkGcpkms)
  testFixturesImplementation(project(":wisp:wisp-deployment"))
  testFixturesImplementation(project(":misk-config"))

  testImplementation("com.squareup.okio:okio:3.16.4")
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.logbackClassic)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-crypto"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-crypto")))

  testImplementation(libs.bouncyCastleProvider)
  testImplementation(libs.guice)
  testImplementation(libs.tink)
  testImplementation(libs.tinkGcpkms)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(project(":misk-config"))

  testFixturesImplementation(libs.bouncyCastleProvider)
  testFixturesImplementation(libs.bouncyCastlePgp)
  testFixturesImplementation(libs.guava)
  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(libs.moshiCore)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(libs.tinkGcpkms)
  testFixturesImplementation(project(":misk-logging"))
  testFixturesImplementation(project(":misk"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
