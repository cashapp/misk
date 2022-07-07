plugins {
  kotlin("jvm")
  `java-library`
  
  id("com.squareup.wire")
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
  implementation(Dependencies.apacheCommonsLang3)
  implementation(Dependencies.bouncycastle)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.javaxInject)
  implementation(Dependencies.okHttp)
  implementation(Dependencies.okio)
  implementation(Dependencies.kotlinReflection)
  implementation(Dependencies.moshiCore)
  implementation(Dependencies.moshiKotlin)
  implementation(Dependencies.moshiAdapters)
  implementation(Dependencies.httpComponentsCore5)
  implementation(Dependencies.jettyHttp2)
  implementation(Dependencies.jettyServer)
  implementation(Dependencies.jettyUnixSocket)
  implementation(Dependencies.servletApi)
  implementation(Dependencies.jettyAlpnJava)
  implementation(Dependencies.jettyServlet)
  implementation(Dependencies.jettyServlets)
  implementation(Dependencies.jettyWebsocketServlet)
  implementation(Dependencies.jettyWebsocketServer)
  implementation(Dependencies.loggingApi)
  implementation(Dependencies.wireGrpcClient)
  implementation(Dependencies.wireMoshiAdapter)
  implementation(Dependencies.wireRuntime)
  implementation(Dependencies.jacksonDatabind)
  implementation(Dependencies.jacksonDataformatYaml)
  implementation(Dependencies.jacksonKotlin)
  implementation(Dependencies.jacksonJsr310)
  implementation(Dependencies.jCommander)
  implementation(Dependencies.openTracing)
  implementation(Dependencies.openTracingUtil)
  implementation(Dependencies.openTracingOkHttp)
  implementation(Dependencies.retrofit)
  implementation(Dependencies.retrofitMoshi)
  implementation(Dependencies.retrofitProtobuf)
  implementation(Dependencies.retrofitWire)
  implementation(Dependencies.jaxbApi)
  implementation(Dependencies.prometheusClient)
  implementation(Dependencies.prometheusHotspot)
  implementation(Dependencies.jnrUnixsocket)
  implementation(Dependencies.concurrencyLimitsCore)
  implementation(project(":misk-core"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  implementation(project(":misk-service"))
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-clustering"))
  api(project(":misk-inject"))
  api(Dependencies.wispClient)
  api(Dependencies.wispConfig)
  api(Dependencies.wispDeployment)
  api(Dependencies.wispDeploymentTesting)
  api(Dependencies.wispLogging)
  api(Dependencies.wispMoshi)

  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(project(":misk-metrics-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.junit4Api)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.okHttpMockWebServer) {
    exclude(group = "junit")
  }
  testImplementation(Dependencies.openTracingMock)
  testImplementation(Dependencies.guavaTestLib)
}

val generatedSourceDir = "$buildDir/generated/source/wire-test"

wire {
  sourcePath {
    srcDir("src/test/proto/")
  }
  java {
    out = generatedSourceDir
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
