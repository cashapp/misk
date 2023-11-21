import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  id("com.squareup.wire")
}

dependencies {
  api(Dependencies.concurrencyLimitsCore)
  api(Dependencies.guava)
  api(Dependencies.guice)
  api(Dependencies.jakartaInject)
  api(Dependencies.jacksonAnotations)
  api(Dependencies.jacksonDatabind)
  api(Dependencies.jakartaInject)
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
  api(project(":wisp:wisp-client"))
  api(project(":wisp:wisp-config"))
  api(project(":wisp:wisp-deployment"))
  api(project(":misk-action-scopes"))
  api(project(":misk-actions"))
  api(project(":misk-clustering"))
  api(project(":misk-config"))
  api(project(":misk-core"))
  api(project(":misk-inject"))
  api(project(":misk-metrics"))
  implementation(Dependencies.jCommander)
  implementation(Dependencies.jettyAlpnServer)
  implementation(Dependencies.jettyHttp)
  implementation(Dependencies.jettyHttp2)
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
  implementation(project(":wisp:wisp-deployment-testing"))
  implementation(project(":wisp:wisp-logging"))
  implementation(project(":wisp:wisp-moshi"))
  implementation(project(":wisp:wisp-ssl"))
  implementation(project(":wisp:wisp-tracing"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-proto"))
  implementation(project(":misk-service"))
  runtimeOnly(Dependencies.jettyAlpnServerJava)

  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.guavaTestLib)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.logbackClassic)
  testImplementation(Dependencies.okHttpMockWebServer)
  testImplementation(Dependencies.openTracingMock)
  testImplementation(project(":wisp:wisp-logging-testing"))
  testImplementation(project(":wisp:wisp-time-testing"))
  testImplementation(project(":misk"))
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

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
