import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(project(":misk-inject"))
  api(project(":misk-action-scopes"))
  api(libs.micrometerCore)
  implementation(project(":misk"))
  implementation(libs.findbugsJsr305)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLibJdk8)

  testImplementation(project(":misk-testing"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.guava)
  testImplementation(libs.guice)
  testImplementation(libs.kotlinStdLibJdk8)
  testImplementation(libs.okHttp)
  testImplementation(libs.okHttpMockWebServer)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
