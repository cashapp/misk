import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-test-fixtures")
  id("org.jetbrains.kotlin.plugin.serialization")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.kotlinxSerializationJson)
  api(libs.mcpKotlinSdkCore)
  api(libs.mcpKotlinSdkServer)
  api(project(":misk-actions"))
  api(project(":misk-api"))
  api(project(":misk-inject"))
  api(project(":misk"))
  api(project(":misk-config"))

  implementation(platform(libs.ktorBom))
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.kotlinxSerializationCore)

  implementation(libs.loggingApi)
  implementation(project(":misk-core"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-metrics"))
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.prometheusClient)

  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockk)
  testImplementation(testFixtures(project(":misk-mcp")))
  testImplementation(testFixtures(project(":misk-metrics")))
  testImplementation(project(":misk-testing"))

  testFixturesApi(libs.mcpKotlinSdkClient)
  testFixturesImplementation(libs.kotlinxSerializationJson)
  testFixturesImplementation(platform(libs.ktorBom))
  testFixturesImplementation(libs.ktorClientContentNegotiation)
  testFixturesImplementation(libs.ktorClientCore)
  testFixturesImplementation(libs.ktorClientEncoding)
  testFixturesImplementation(libs.ktorClientLogging)
  testFixturesImplementation(libs.ktorClientOkhttp)
  testFixturesImplementation(libs.ktorSerializationKotlinxJson)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
