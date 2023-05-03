import com.google.protobuf.gradle.*

plugins {
  kotlin("jvm")
  `java-library`

  id("com.google.protobuf")
  id("com.squareup.wire")
}

protobuf {
  plugins {
    id("grpc") {
      artifact = Dependencies.grpcGenJava
    }
  }

  protoc {
    artifact = Dependencies.protoc
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
  val main by getting {
    java.srcDir("build/generated/source/proto/main/grpc")
    java.srcDir("build/generated/source/proto/main/java")
    java.srcDir("build/generated/source/wire")
  }

  // TODO(jwilson): we do this to make IntelliJ happy but the Wire Gradle plugin should do that.
}

dependencies {
  api(Dependencies.grpcApi)
  api(Dependencies.grpcStub)
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.okHttp)
  api(Dependencies.okio)
  api(Dependencies.protobufJava)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(Dependencies.grpcNetty)
  implementation(Dependencies.grpcProtobuf)
  implementation(Dependencies.javaxAnnotation)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.nettyHandler)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireRuntime)
  implementation(project(":misk-core"))
  implementation(project(":misk-metrics-testing"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.awaitility)
  testImplementation(Dependencies.awaitilityKotlin)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.protoGoogleCommon)
  testImplementation(Dependencies.wispLoggingTesting)
}
