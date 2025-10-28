import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  api(libs.concurrencyLimitsCore)
  api(libs.guava)
  api(libs.guice)
  api(libs.jacksonAnnotations)
  api(libs.jacksonDatabind)
  api(libs.jakartaInject)
  api(libs.jakartaInject)
  api(libs.jettyHttp2Common)
  api(libs.jettyIo)
  api(libs.jettyServer)
  api(libs.jettyServletApi)
  api(libs.jettyUtil)
  api(libs.kotlinXHtml)
  api(libs.moshiCore)
  api(libs.okHttp)
  api(libs.openTracing)
  api(libs.prometheusClient)
  api(libs.retrofit)
  api(libs.servletApi)
  api(libs.slf4jApi)
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-audit-client"))
  api(project(":misk-backoff"))
  api(project(":misk-clustering"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  api(project(":misk-moshi"))
  api(project(":misk-sampling"))
  api(project(":misk-service"))
  api(project(":wisp:wisp-deployment"))
  implementation(libs.jCommander)
  implementation(libs.jettyAlpnServer)
  implementation(libs.jettyHttp)
  implementation(libs.jettyHttp2)
  implementation(libs.jettyServlet)
  implementation(libs.jettyServlets)
  implementation(libs.jettyUds)
  implementation(libs.jettyUnixSocket)
  implementation(libs.jettyWebsocketApi)
  implementation(libs.jettyWebsocketServer)
  implementation(libs.jnrUnixsocket)
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.kotlinxCoroutinesSlf4j)
  implementation(libs.logbackClassic)
  implementation(libs.loggingApi)
  implementation(libs.okio)
  implementation(libs.openTracingConcurrent)
  implementation(libs.openTracingOkHttp)
  implementation(libs.retrofitMoshi)
  implementation(libs.retrofitProtobuf)
  implementation(libs.retrofitWire)
  implementation(libs.wireGrpcClient)
  implementation(libs.wireRuntime)
  implementation(project(":misk-feature"))
  implementation(project(":misk-grpc-reflect"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  implementation(project(":misk-tailwind"))
  implementation(project(":misk-tokens"))
  implementation(project(":wisp:wisp-deployment-testing"))
  implementation(project(":wisp:wisp-moshi"))
  runtimeOnly(libs.jettyAlpnServerJava)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  testImplementation(libs.assertj)
  testImplementation(libs.awaitility)
  testImplementation(libs.awaitilityKotlin)
  testImplementation(libs.guavaTestLib)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesCore)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockitoCore)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(libs.okHttpSse)
  testImplementation(libs.openTracingMock)
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-tracing"))
  testImplementation(testFixtures(project(":misk-audit-client")))
  testImplementation(testFixtures(project(":misk-metrics")))
}

val generatedSourceDir = layout.buildDirectory.dir("generated/source/wire-test").get().asFile.path

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
    main {
      java.setSrcDirs(java.srcDirs.filter { !it.path.contains(generatedSourceDir) })
    }
    test {
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
