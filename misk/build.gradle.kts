import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  alias(libs.plugins.wire)
}

dependencies {
  api(libs.concurrencyLimitsCore)
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.jacksonAnnotations)
  api(libs.jacksonDatabind)
  api(libs.jakartaInject)
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
  api(project(":wisp:wisp-client"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-backoff"))
  api(project(":misk-clustering"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  api(project(":misk-service"))
  implementation(libs.jCommander)
  implementation(libs.jettyAlpnServer)
  implementation(libs.jettyHttp)
  implementation(libs.jettyHttp2)
  implementation(libs.jettyIo)
  implementation(libs.jettyServlet)
  implementation(libs.jettyServlets)
  implementation(libs.jettyUds)
  implementation(libs.jettyUnixSocket)
  implementation(libs.jettyWebsocketApi)
  implementation(libs.jettyWebsocketServer)
  implementation(libs.loggingApi)
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.kotlinxCoroutinesCore)
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
  implementation(project(":misk-tailwind"))
  implementation(project(":wisp:wisp-deployment-testing"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":wisp:wisp-ssl"))
  implementation(project(":wisp:wisp-tracing"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  runtimeOnly(libs.jettyAlpnServerJava)

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  testImplementation(libs.assertj)
  testImplementation(libs.guavaTestLib)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotestAssertions)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.logbackClassic)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(libs.openTracingMock)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk"))
  testImplementation(project(":misk-testing"))
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
