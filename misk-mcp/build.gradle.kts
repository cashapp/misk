import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guice)
  api(libs.jakartaInject)
  api(libs.kotlinxSerializationJson)
  api(libs.mcpKotlinSdk)
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
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
