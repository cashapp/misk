import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(libs.aws2Auth)
  implementation(libs.aws2Regions)
  api(libs.aws2Sqs)
  api(libs.guava)
  api(libs.guice)
  api(libs.kotlinxCoroutinesCore)
  api(libs.jakartaInject)
  api(libs.moshiCore)
  api(libs.prometheusClient)
  api(project(":wisp:wisp-config"))
  implementation(libs.aws2Core)
  implementation(project(":misk"))
  implementation(project(":misk-api"))
  api(project(":misk-aws"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  implementation(libs.loggingApi)
  runtimeOnly(libs.openTracingDatadog)
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-service"))
  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.dockerApi)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.mockitoKotlin)
  testImplementation(project(":wisp:wisp-containers-testing"))
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
