@Suppress("DSL_SCOPE_VIOLATION")
plugins {
  kotlin("jvm")
  `java-library`

  alias(libs.plugins.wireGradlePlugin)
}

dependencies {
  api(libs.concurrencyLimitsCore)
  api(libs.guice)
  api(libs.jacksonAnotations)
  api(libs.jacksonDatabind)
  api(libs.javaxInject)
  api(libs.jettyServer)
  api(libs.jettyServletApi)
  api(libs.jettyUtil)
  api(libs.moshi)
  api(libs.okHttp)
  api(libs.openTracingApi)
  api(libs.prometheusClient)
  api(libs.retrofit)
  api(libs.servletApi)
  api(libs.slf4jApi)
  api(libs.wispClient)
  api(libs.wispConfig)
  api(libs.wispDeployment)
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-clustering"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(libs.guava)
  implementation(libs.jCommander)
  implementation(libs.jettyAlpnServer)
  implementation(libs.jettyHttp)
  implementation(libs.jettyHttp2)
  implementation(libs.jettyHttp2Common)
  implementation(libs.jettyIo)
  implementation(libs.jettyServlet)
  implementation(libs.jettyServlets)
  implementation(libs.jettyUnixSocket)
  implementation(libs.jettyWebsocketApi)
  implementation(libs.jettyWebsocketServer)
  implementation(libs.kotlinLogging)
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.moshiAdapters)
  implementation(libs.okio)
  implementation(libs.openTracingConcurrent)
  implementation(libs.openTracingOkHttp)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitProtobuf)
  implementation(libs.retrofitWire)
  implementation(libs.wireGrpcClient)
  implementation(libs.wireMoshiAdapter)
  implementation(libs.wireRuntime)
  implementation(libs.wispDeploymentTesting)
  implementation(libs.wispLogging)
  implementation(libs.wispMoshi)
  implementation(libs.wispSsl)
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  implementation(project(":misk-service"))
  runtimeOnly(libs.jettyAlpnServerJava)

  testImplementation(libs.assertj)
  testImplementation(libs.guavaTestLib)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutines)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(libs.openTracingMock)
  testImplementation(libs.wispLoggingTesting)
  testImplementation(libs.wispTimeTesting)
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
