import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting"))
  api(project(":wisp:wisp-rate-limiting:bucket4j"))
  api(libs.awsDynamodb)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.micrometerCore)

  implementation(project(":misk-logging"))
  implementation(libs.bucket4jCore)
  implementation(libs.bucket4jDynamoDbV1)
  implementation(libs.loggingApi)

  testImplementation(project(":misk"))
  testImplementation(project(":misk-rate-limiting-bucket4j-dynamodb-v1"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-deployment"))
  testImplementation(testFixtures(project(":misk-aws-dynamodb")))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)

}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
