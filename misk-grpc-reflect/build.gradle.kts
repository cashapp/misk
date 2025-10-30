import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

wire {
  sourcePath {
    srcJar(libs.wireReflector)
  }
  // Generate service interfaces only; the client comes with wire-reflector.
  kotlin {
    includes = listOf("grpc.reflection.v1alpha.ServerReflection")
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.wireReflector)
  api(libs.wireSchema)
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(libs.loggingApi)
  implementation(libs.kotlinReflect)
  implementation(libs.okio)
  implementation(libs.wireRuntime)
  implementation(project(":misk-logging"))

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.okHttp)
  testImplementation(libs.protobufJava)
  testImplementation(libs.slf4jApi)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-grpc-tests"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
