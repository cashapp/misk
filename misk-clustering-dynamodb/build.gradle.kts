import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.loggingApi)
  api(project(":misk-inject"))
  api(project(":wisp:wisp-config"))

  implementation(libs.aws2Dynamodb)
  implementation(libs.aws2DynamodbEnhanced)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(project(":misk"))
  implementation(project(":misk-backoff"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
  testImplementation(testFixtures(project(":misk-aws2-dynamodb")))

  if (org.apache.tools.ant.taskdefs.condition.Os.isArch("aarch64")) {
    // Without this, we can't compile on Apple Silicon currently.
    // This is likely not necessary to have long term,
    // so we should remove it when things get fixed upstream.
    testImplementation("io.github.ganadist.sqlite4java:libsqlite4java-osx-aarch64:1.0.392")
  }

  // Have to clamp until DynamoDBLocal supports later versions (dependency from tempest).
  testRuntimeOnly("org.antlr:antlr4-runtime") {
    version {
      strictly("4.9.3")
    }
  }
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
