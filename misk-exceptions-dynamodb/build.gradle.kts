import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.awsJavaSdkCore)
  api(libs.jakartaInject)
  api(libs.slf4jApi)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(libs.guice)
  implementation(libs.okHttp)
  implementation(project(":misk-core"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
