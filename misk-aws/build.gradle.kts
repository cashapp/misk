import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.awsSdkCore)
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
  implementation(libs.moshiCore)
  implementation(libs.openTracing)
  implementation(libs.openTracingDatadog)
  implementation(libs.prometheusClient)
  implementation(libs.slf4jApi)
  implementation(libs.tracingDatadog)
  implementation(project(":misk-api"))
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
  testImplementation(testFixtures(project(":misk-feature")))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
