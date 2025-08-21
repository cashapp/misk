plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.squareup.wire")
  id("application")
}

val applicationMainClass = "com.squareup.exemplar.ExemplarServiceKt"
application {
  mainClass.set(applicationMainClass)
}

dependencies {
  compileOnly(libs.findbugsJsr305)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.loggingApi)
  implementation(libs.kotlinXHtml)
  implementation(libs.logbackClassic)
  implementation(libs.moshiCore)
  implementation(libs.slf4jApi)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(project(":wisp:wisp-config"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(project(":wisp:wisp-lease"))
  implementation(project(":misk-logging"))
  implementation(project(":wisp:wisp-rate-limiting"))
  implementation(project(":wisp:wisp-token"))
  implementation(project(":misk"))
  implementation(project(":misk-actions"))
  implementation(project(":misk-admin"))
  implementation(project(":misk-api"))
  implementation(project(":misk-audit-client"))
  implementation(project(":misk-config"))
  implementation(project(":misk-clustering"))
  implementation(project(":misk-core"))
  implementation(project(":misk-cron"))
  implementation(project(":misk-inject"))
  implementation(project(":misk-jdbc"))
  implementation(project(":misk-hotwire"))
  implementation(project(":misk-lease-mysql"))
  implementation(project(":misk-prometheus"))
  implementation(project(":misk-service"))
  implementation(project(":misk-tailwind"))
  implementation(project(":misk-tokens"))
  implementation(project(":misk-testing"))

  testImplementation(libs.assertj)
  testImplementation(libs.awsDynamodb)
  testImplementation(libs.jedis)
  testImplementation(libs.junitApi)
  testImplementation(libs.micrometerCore)
  testImplementation(libs.okHttpMockWebServer)
  testImplementation(project(":misk-hibernate"))
  testImplementation(project(":misk-jdbc"))
  testImplementation(project(":misk-rate-limiting-bucket4j-dynamodb-v1"))
  testImplementation(project(":misk-rate-limiting-bucket4j-mysql"))
  testImplementation(project(":misk-rate-limiting-bucket4j-redis"))
  testImplementation(project(":misk-redis"))
  testImplementation(project(":misk-testing"))
  testImplementation(project(":wisp:wisp-logging-testing"))

  testImplementation(testFixtures(project(":misk-aws-dynamodb")))
  testImplementation(testFixtures(project(":misk-jdbc")))
  testImplementation(testFixtures(project(":misk-redis")))
}

tasks.jar {
  manifest {
    attributes("Main-Class" to applicationMainClass)
  }
  archiveClassifier.set("unshaded")
}

wire {
  // Necessary to support Grpc Reflection so proto files are available in the runtime classpath, otherwise can remove
  protoLibrary = true

  // Generate service interfaces also.
  kotlin {
    includes = listOf("com.squareup.exemplar.protos.HelloWebService")
    rpcRole = "server"
    rpcCallStyle = "blocking"
    singleMethodServices = true
  }

  kotlin {
    rpcRole = "client"
    javaInterop = true
  }
}
