import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.awsS3)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tink)
  api(Dependencies.tinkAwskms)
  api(Dependencies.tinkGcpkms)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.bouncycastlePgp)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.moshi)
  implementation(Dependencies.okio)
  implementation(Dependencies.wispLogging)
  implementation(project(":misk"))

  testFixturesApi(project(":misk-crypto"))
  testFixturesApi(project(":misk-inject"))
  testFixturesImplementation(Dependencies.bouncycastle)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.tink)
  testFixturesImplementation(Dependencies.tinkAwskms)
  testFixturesImplementation(Dependencies.tinkGcpkms)
  testFixturesImplementation(Dependencies.wispDeployment)
  testFixturesImplementation(project(":misk-config"))

  testImplementation("com.squareup.okio:okio:3.3.0")
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(project(":misk-crypto"))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-crypto")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
