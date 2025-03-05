import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.aws2Auth)
  api(libs.aws2Sqs)
  api(libs.guava)
  api(libs.guice)
  api(libs.kotlinxCoroutinesCore)
  api(libs.jakartaInject)
  api(libs.moshiCore)
  api(libs.prometheusClient)
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-token"))
  implementation(libs.aws2Core)
  implementation(libs.aws2Regions)
  implementation(libs.loggingApi)
  implementation(project(":misk"))
  implementation(project(":misk-api"))
  implementation(project(":misk-core"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))
  runtimeOnly(libs.openTracingDatadog)
  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.dockerApi)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.mockitoKotlin)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-containers-testing"))
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
