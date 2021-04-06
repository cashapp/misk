import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.ofSourceSet
import com.google.protobuf.gradle.plugins
import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
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
  implementation(Dependencies.assertj)
  implementation(Dependencies.junitApi)
  implementation(Dependencies.kotlinTest)
  implementation(Dependencies.docker)
  implementation(Dependencies.guice)
  implementation(Dependencies.grpcNetty)
  implementation(Dependencies.grpcProtobuf)
  implementation(Dependencies.grpcStub)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireRuntime)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))

  testImplementation(Dependencies.logbackClassic)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
