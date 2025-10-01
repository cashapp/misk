import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.awsCore)
  api(libs.awsS3)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.tink)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.bouncyCastleProvider)
  implementation(libs.bouncyCastlePgp)
  implementation(libs.guava)
  implementation(libs.loggingApi)
  implementation(libs.moshiCore)
  implementation(libs.okio)
  implementation(libs.tinkAwskms)
  implementation(libs.tinkGcpkms)
  implementation(project(":misk-logging"))
  implementation(project(":misk-moshi"))

  runtimeOnly(libs.bouncyCastlePkix)

  testFixturesApi(project(":misk-crypto"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(libs.bouncyCastleProvider)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.tink)
  testFixturesImplementation(libs.tinkAwskms)
  testFixturesImplementation(libs.tinkGcpkms)
  testFixturesImplementation(project(":wisp:wisp-deployment"))
  testFixturesImplementation(project(":misk-config"))

  testImplementation("com.squareup.okio:okio:3.3.0")
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
  testImplementation(libs.tinkAwskms)
  testImplementation(libs.tinkGcpkms)
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(project(":misk-config"))

  testFixturesImplementation(libs.bouncyCastleProvider)
  testFixturesImplementation(libs.bouncyCastlePgp)
  testFixturesImplementation(libs.guava)
  testFixturesImplementation(libs.loggingApi)
  testFixturesImplementation(libs.moshiCore)
  testFixturesImplementation(libs.okio)
  testFixturesImplementation(libs.tinkAwskms)
  testFixturesImplementation(libs.tinkGcpkms)
  testFixturesImplementation(project(":misk-logging"))
  testFixturesImplementation(project(":misk"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
