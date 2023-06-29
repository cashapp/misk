import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.okHttp)
  api(Dependencies.wireGrpcClient) // GrpcStatus
  api(Dependencies.wireRuntime) // AnyMessage
  api(project(":misk-inject"))
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.okio)

  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
