import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.awsCore)
  api(libs.awsS3)
  api(libs.awsSqs)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":wisp:wisp-aws-environment"))
  api(project(":wisp:wisp-lease"))
  api(project(":misk"))
  api(project(":misk-config"))
  api(project(":misk-feature"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  api(project(":wisp:wisp-deployment"))
  implementation(libs.loggingApi)
  implementation(libs.moshiCore)
  implementation(libs.openTracing)
  implementation(libs.openTracingDatadog)
  implementation(libs.prometheusClient)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":misk-api"))
  implementation(project(":misk-moshi"))
  implementation(project(":misk-testing"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-backoff"))
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
  testImplementation(libs.mockitoKotlin)
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-clustering"))
  testImplementation(testFixtures(project(":misk-feature")))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
