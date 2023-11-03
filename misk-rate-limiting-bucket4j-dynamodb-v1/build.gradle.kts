import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

dependencies {
  api(project(":misk-aws-dynamodb"))
  api(project(":misk-inject"))
  api(project(":wisp:wisp-rate-limiting:bucket4j"))

  implementation(project(":misk-inject"))
  implementation(Dependencies.bucket4jDynamoDbV1)

  testImplementation(Dependencies.micrometerPrometheus)
  testImplementation(project(":misk"))
  testImplementation(testFixtures(project(":misk-aws-dynamodb")))
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":wisp:wisp-rate-limiting")))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
