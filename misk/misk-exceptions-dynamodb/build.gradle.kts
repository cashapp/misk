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
  api(Dependencies.awsJavaSdkCore)
  api(Dependencies.javaxInject)
  api(Dependencies.slf4jApi)
  api(project(":misk:misk"))
  api(project(":misk:misk-actions"))
  api(project(":misk:misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.okHttp)
  implementation(project(":misk:misk-core"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk:misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
