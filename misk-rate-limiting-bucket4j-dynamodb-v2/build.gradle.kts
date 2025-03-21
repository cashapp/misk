import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
}

dependencies {
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting"))
  api(project(":wisp:wisp-rate-limiting:bucket4j"))
  api(libs.aws2Dynamodb)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.micrometerCore)

  implementation(project(":wisp:wisp-logging"))
  implementation(libs.awsSdkCore)
  implementation(libs.bucket4jCore)
  implementation(libs.loggingApi)

  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-aws2-dynamodb")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)

  testFixturesImplementation(project(":wisp:wisp-logging"))
  testFixturesImplementation(libs.bucket4jCore)
  testFixturesImplementation(libs.loggingApi)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
