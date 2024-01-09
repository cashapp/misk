import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.okHttp)
  api(libs.wireGrpcClient) // GrpcStatus
  api(libs.wireRuntime) // AnyMessage
  api(project(":misk-inject"))
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.okio)

  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
