import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.awsJavaSdkCore)
  api(libs.awsS3)
  api(libs.awsSqs)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":wisp:wisp-aws-environment"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-lease"))
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  implementation(libs.kotlinLogging)
  implementation(libs.moshi)
  implementation(libs.openTracingApi)
  implementation(libs.openTracingDatadog)
  implementation(libs.prometheusClient)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-tracing"))
  implementation(project(":misk-core"))
  implementation(project(":misk-hibernate"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":misk-transactional-jobqueue"))
  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.dockerApi)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.mockitoCore)
  testImplementation(project(":wisp:wisp-containers-testing"))
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-feature-testing"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
