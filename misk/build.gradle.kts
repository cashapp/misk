sourceSets {
  val main by getting {
    resources.srcDir(listOf(
      "web/tabs/admin-dashboard/lib",
      "web/tabs/config/lib",
      "web/tabs/database/lib",
      "web/tabs/web-actions/lib"
    ))
    resources.exclude("**/node_modules")
  }
  val test by getting {
    java.srcDir("src/test/kotlin/")
  }
}

dependencies {
  implementation(Dependencies.kotlinStdLibJdk8)
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
  implementation(Dependencies.jettyHttp2)
  implementation(Dependencies.jettyServer)
  implementation(Dependencies.jettyUnixSocket)
  implementation(Dependencies.servletApi)
  implementation(Dependencies.jettyAlpnJava)
  implementation(Dependencies.jettyServlet)
  implementation(Dependencies.jettyServlets)
  implementation(Dependencies.jettyWebsocketServlet)
  implementation(Dependencies.jettyWebsocketServer)
  implementation(Dependencies.kubernetesClient)
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
  implementation(project(":misk-actions"))
  implementation(project(":misk-core"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-service"))
  api(project(":misk-inject"))
  api(project(":wisp-client"))
  api(project(":wisp-config"))
  api(project(":wisp-deployment"))
  api(project(":wisp-deployment-testing"))  // for fake implementation
  api(project(":wisp-logging"))

  testImplementation(Dependencies.kotlinxCoroutines)
  testImplementation(Dependencies.mockitoCore)
  testImplementation(project(":misk-metrics-testing"))
  testImplementation(project(":misk-testing"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.okHttpMockWebServer) {
    exclude(group = "junit")
  }
  testImplementation(Dependencies.openTracingMock)
  testImplementation(Dependencies.guavaTestLib)
}

afterEvaluate {
  project.tasks.dokka {
    outputDirectory = "$rootDir/docs/0.x"
    outputFormat = "gfm"
  }
}

apply(from = "$rootDir/gradle-mvn-publish.gradle")
