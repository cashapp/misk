import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
