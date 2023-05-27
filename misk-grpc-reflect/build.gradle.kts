import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  
  id("com.squareup.wire")
}

wire {
  sourcePath {
    srcJar(Dependencies.wireReflector)
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
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.wireReflector)
  api(project(":misk-actions"))
  api(project(":misk-inject"))
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.okio)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.wireSchema)
  implementation(Dependencies.wispLogging)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.okHttp)
  testImplementation(Dependencies.protobufJava)
  testImplementation(Dependencies.slf4jApi)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-core"))
  testImplementation(project(":misk-grpc-tests"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
