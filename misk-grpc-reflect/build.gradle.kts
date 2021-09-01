plugins {
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
  implementation(Dependencies.assertj)
  implementation(Dependencies.docker)
  implementation(Dependencies.grpcNetty)
  implementation(Dependencies.grpcProtobuf)
  implementation(Dependencies.grpcStub)
  implementation(Dependencies.guice)
  implementation(Dependencies.junitApi)
  implementation(Dependencies.kotlinTest)
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
  implementation(project(":misk-testing"))

  testImplementation(Dependencies.logbackClassic)
  testImplementation(project(":misk-grpc-tests"))
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
