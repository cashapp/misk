import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  // Core AWS SDK 2.x dependencies
  api(libs.aws2Auth)
  api(libs.aws2S3)
  api(libs.guice6)
  api(libs.jakartaInject)
  
  // Misk core dependencies
  api(project(":misk-aws"))  // For AwsRegion
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  
  // Implementation dependencies
  implementation(libs.aws2Core)
  implementation(libs.aws2Regions)
  
  // Runtime-only dependencies
  runtimeOnly(libs.openTracingDatadog)
  
  testImplementation(libs.assertj)
  testImplementation(libs.awaitilityKotlin)
  testImplementation(libs.dockerApi)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.mockitoKotlin)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-feature-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))

  // Test fixtures dependencies
  testFixturesApi(libs.dockerApi)
  testFixturesApi(libs.junitApi)
  testFixturesApi(project(":misk-testing"))
  testFixturesImplementation(libs.aws2S3)
  testFixturesImplementation(libs.aws2Auth)
  testFixturesImplementation(libs.aws2Regions)
  testFixturesImplementation(project(":misk-aws"))
  testFixturesImplementation(project(":misk-inject"))
  testFixturesImplementation(project(":wisp:wisp-logging"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
