plugins {
  kotlin("jvm")
  `java-library`

  id("com.squareup.wire")
}

dependencies {
  api(Dependencies.concurrencyLimitsCore)
  api(Dependencies.guice)
  api(Dependencies.jacksonAnotations)
  api(Dependencies.jacksonDatabind)
  api(Dependencies.javaxInject)
  api(Dependencies.jettyServer)
  api(Dependencies.jettyServletApi)
  api(Dependencies.jettyUtil)
  api(Dependencies.moshi)
  api(Dependencies.okHttp)
  api(Dependencies.openTracingApi)
  api(Dependencies.prometheusClient)
  api(Dependencies.retrofit)
  api(Dependencies.servletApi)
  api(Dependencies.slf4jApi)
  api(Dependencies.wispClient)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-clustering"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.guava)
  implementation(Dependencies.jCommander)
  implementation(Dependencies.jettyAlpnServer)
  implementation(Dependencies.jettyHttp)
  implementation(Dependencies.jettyHttp2)
  implementation(Dependencies.jettyHttp2Common)
  implementation(Dependencies.jettyIo)
  implementation(Dependencies.jettyServlet)
  implementation(Dependencies.jettyServlets)
  implementation(Dependencies.jettyUnixSocket)
  implementation(Dependencies.jettyWebsocketApi)
  implementation(Dependencies.jettyWebsocketServer)
  implementation(Dependencies.kotlinLogging)
  implementation(Dependencies.kotlinReflect)
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.okio)
  implementation(Dependencies.openTracingConcurrent)
  implementation(Dependencies.openTracingOkHttp)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitProtobuf)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.wispDeploymentTesting)
  implementation(Dependencies.wispLogging)
  implementation(Dependencies.wispMoshi)
  implementation(Dependencies.wispSsl)
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  implementation(project(":misk-service"))
  runtimeOnly(Dependencies.jettyAlpnServerJava)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guavaTestLib)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation(Dependencies.openTracingMock)
  testImplementation(Dependencies.wispLoggingTesting)
  testImplementation(Dependencies.wispTimeTesting)
  testImplementation(project(":misk-metrics-testing"))
  testImplementation(project(":misk-testing"))
}

val generatedSourceDir = "$buildDir/generated/source/wire-test"

wire {
  sourcePath {
    srcDir("src/test/proto/")
  }
  java {
    out = generatedSourceDir
    exclusive = false
  }

  kotlin {
    out = generatedSourceDir
    rpcRole = "client"
    rpcCallStyle = "blocking"
    exclusive = false
    includes = listOf(
      "helloworld.Greeter"
    )
  }

  kotlin {
    out = generatedSourceDir
    rpcRole = "server"
    rpcCallStyle = "blocking"
    exclusive = false
    singleMethodServices = true
    includes = listOf(
      "helloworld.Greeter"
    )
  }
}

// Make sure the Wire-generated sources are test-only.
afterEvaluate {
  val generatedSourceGlob = "$generatedSourceDir/**"

  sourceSets {
    val main by getting {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(generatedSourceDir) })
    }
    val test by getting {
      java.srcDir(generatedSourceDir)
    }
  }

  tasks {
    compileJava {
      exclude(generatedSourceGlob)
    }
    compileTestJava {
      include(generatedSourceGlob)
    }
  }
}
