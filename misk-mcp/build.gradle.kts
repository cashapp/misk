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
  api(libs.kotlinxSerializationJson)

  api(project(":misk-actions"))
  api(project(":misk-inject"))
  api(project(":misk"))
  api(project(":misk-config"))
  api(libs.kotlinxCoroutinesCore)
  api(libs.kotlinxSerializationCore)

  implementation(libs.jakartaInject)
  implementation(libs.kotlinReflect)

  implementation(libs.loggingApi)
  implementation(project(":misk-api"))
  implementation(project(":misk-core"))
  implementation(project(":misk-crypto"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-metrics"))
  implementation(project(":misk-moshi"))
  implementation(libs.moshiCore)
  implementation(libs.okHttp)
  implementation(libs.okio)
  implementation(libs.prometheusClient)
  implementation(libs.tink)

  testImplementation(libs.jsonSchemaValidator)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.ktorClientContentNegotiation)
  testImplementation(libs.ktorClientCore)
  testImplementation(libs.ktorClientOkhttp)
  testImplementation(libs.ktorSerializationKotlinxJson)
  testImplementation(libs.mcpKotlinSdkClient)
  testImplementation(libs.mockk)
  testImplementation(testFixtures(project(":misk-crypto")))
  testImplementation(testFixtures(project(":misk-mcp")))
  testImplementation(testFixtures(project(":misk-metrics")))
  testImplementation(project(":misk-testing"))

  testFixturesApi(libs.okHttp)
  testFixturesImplementation(libs.kotlinxCoroutinesCore)
  testFixturesImplementation(libs.kotlinxSerializationJson)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
