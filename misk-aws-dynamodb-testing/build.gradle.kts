import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.awsDynamodb)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.tempestTestingInternal)
  api(project(":misk-aws-dynamodb"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.tempestTesting)
  implementation(Dependencies.tempestTestingDocker)
  implementation(Dependencies.tempestTestingJvm)
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
