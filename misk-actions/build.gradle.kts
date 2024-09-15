import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
