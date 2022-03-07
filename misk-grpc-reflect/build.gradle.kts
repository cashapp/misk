plugins {
  kotlin("jvm")
  `java-library`
  
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
  implementation(Dependencies.dockerCore)
  implementation(Dependencies.dockerTransport)
  implementation(Dependencies.grpcNetty)
  implementation(Dependencies.grpcProtobuf)
  implementation(Dependencies.grpcStub)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinxCoroutines)
  implementation(Dependencies.okio)
  implementation(Dependencies.wireCompiler)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireReflector)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.wireSchema)
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-service"))

  testImplementation(Dependencies.assertj)
  testImplementation(project(":misk-grpc-tests"))
  testImplementation(project(":misk-testing"))
}
