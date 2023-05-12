import com.autonomousapps.internal.utils.toPrettyString
import com.google.protobuf.gradle.*

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  `java-library`

  alias(libs.plugins.protobufGradlePlugin)
  alias(libs.plugins.wireGradlePlugin)
}

protobuf {
  plugins {
    id("grpc") {
      artifacts
      val grpcGenJava = libs.grpcGenJava.get()
      artifact = "${grpcGenJava.group}:${grpcGenJava.name}:${grpcGenJava.version}"
    }
  }

  protoc {
    val protoc = libs.protoc.get()
    artifact = "${protoc.group}:${protoc.name}:${protoc.version}"
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
  api(libs.grpcApi)
  api(libs.grpcStub)
  api(libs.guice)
  api(libs.javaxInject)
  api(libs.okHttp)
  api(libs.okio)
  api(libs.protobufJava)
  api(project(":misk"))
  api(project(":misk-actions"))
  api(project(":misk-config"))
  api(project(":misk-inject"))
  implementation(libs.grpcNetty)
  implementation(libs.grpcProtobuf)
  implementation(libs.javaxAnnotation)
  implementation(libs.kotlinxCoroutines)
  implementation(libs.nettyHandler)
  implementation(libs.wireGrpcClient)
  implementation(libs.wireRuntime)
  implementation(project(":misk-core"))
  implementation(project(":misk-metrics-testing"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.awaitilityKotlin)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.protoGoogleCommon)
  testImplementation(libs.wispLoggingTesting)
}
