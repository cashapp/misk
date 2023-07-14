import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.aws2Dynamodb)
  api(Dependencies.aws2DynamodbEnhanced)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tempest2TestingInternal)
  api(project(":misk:misk-aws2-dynamodb"))
  api(project(":misk:misk-inject"))
  api(project(":misk:misk-testing"))
  implementation(Dependencies.errorproneAnnotations)
  implementation(Dependencies.tempest2Testing)
  implementation(Dependencies.tempest2TestingDocker)
  implementation(Dependencies.tempest2TestingJvm)
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
