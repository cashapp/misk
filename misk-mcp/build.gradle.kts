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
  api(project(":wisp:wisp-config"))

  
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.kotlinxSerializationCore)

  implementation(libs.loggingApi)
  implementation(project(":misk-core"))
  implementation(project(":misk-logging"))
  implementation(libs.okHttp)
  implementation(libs.okio)

  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
  testImplementation(libs.kotlinxCoroutinesTest)
  testImplementation(libs.mockk)
  testImplementation(testFixtures(project(":misk-mcp")))
  testImplementation(project(":misk-testing"))

  testFixturesApi(libs.mcpKotlinSdkClient)
  testFixturesImplementation(libs.kotlinxSerializationJson)
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
