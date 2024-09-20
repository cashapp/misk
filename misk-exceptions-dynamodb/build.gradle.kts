import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.awsDynamodb)
  api(libs.awsCore)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
