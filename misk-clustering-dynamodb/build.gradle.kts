import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.kotlinLogging)
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))
  implementation(Dependencies.aws2Dynamodb)
  implementation(Dependencies.aws2DynamodbEnhanced)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.jakartaInject)
  implementation(project(":misk"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-aws2-dynamodb")))

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently. This is likely not necessary to
    // have longterm, so we should remove it when platform fixes things across Square.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }

  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testRuntimeOnly("org.antlr:antlr4-runtime") {
    version {
      strictly("4.9.3")
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
