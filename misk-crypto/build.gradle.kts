import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(libs.awsSdkCore)
  api(libs.awsS3)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.tink)
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.bouncycastle)
  implementation(libs.bouncycastlePgp)
  implementation(libs.guava)
  implementation(libs.kotlinLogging)
  implementation(libs.moshiCore)
  implementation(libs.okio)
  implementation(libs.tinkAwskms)
  implementation(libs.tinkGcpkms)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk"))

  testFixturesApi(project(":misk-crypto"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(libs.bouncycastle)
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
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
