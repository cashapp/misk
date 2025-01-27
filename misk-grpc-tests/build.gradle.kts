import com.google.protobuf.gradle.*
import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.google.protobuf")
  id("com.squareup.wire")
}

protobuf {
  plugins {
    id("grpc") {
      artifact = "${libs.grpcGenJava.get().group}:${libs.grpcGenJava.get().name}:${libs.grpcGenJava.get().version}"
    }
  }

  protoc {
    artifact = "${libs.protoc.get().group}:${libs.protoc.get().name}:${libs.protoc.get().version}"
  }

  generateProtoTasks {
    ofSourceSet("main").forEach {
      it.plugins {
        // Apply the "grpc" plugin whose spec is defined above, without
        // options.  Note the braces cannot be omitted, otherwise the
        // plugin will not be added. This is because of the implicit way
        // NamedDomainObjectContainer binds the methods.
        id("grpc")
      }
    }
  }
}

wire {
  kotlin {
    rpcRole = "client"
    javaInterop = true
  }

  // Generate service interfaces also.
  kotlin {
    includes = listOf("routeguide.RouteGuide")
    exclusive = false
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }
}

sourceSets {
  main {
    java.srcDir("build/generated/source/proto/main/grpc")
    java.srcDir("build/generated/source/proto/main/java")
    java.srcDir("build/generated/source/wire")
  }

  // TODO(jwilson): we do this to make IntelliJ happy but the Wire Gradle plugin should do that.
}

dependencies {
  api(libs.grpcApi)
  api(libs.grpcStub)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.okHttp)
  api(libs.okio)
  api(libs.protobufJava)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.grpcNetty)
  implementation(libs.grpcProtobuf)
  implementation(libs.javaxAnnotation)
  implementation(libs.nettyHandler)
  implementation(libs.wireGrpcClient)
  implementation(libs.wireRuntime)
  implementation(project(":misk-core"))
  implementation(testFixtures(project(":misk-metrics")))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.awaitilityKotlin)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.googleCommonProtos)
  testImplementation(project(":wisp:wisp-logging-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
