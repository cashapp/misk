
plugins {
  id("com.squareup.wire")
}

wire {
  kotlin {
    rpcRole = "client"
    javaInterop = true
  }

  // Generate service interfaces also.
  kotlin {
    includes = listOf("grpc.reflection.v1alpha.ServerReflection")
    exclusive = false
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }
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
  implementation(Dependencies.wireCompiler)
  implementation(Dependencies.wireSchema)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))
  implementation(project(":misk-testing"))
  implementation(project(":misk-grpc-tests"))

  testImplementation(project(":misk-testing"))
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
