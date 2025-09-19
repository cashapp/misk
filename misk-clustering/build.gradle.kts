import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.guava)
  api(libs.jakartaInject)
  api(libs.loggingApi)
  api(project(":wisp:wisp-config"))
  api(project(":misk-inject"))
  implementation(libs.caffeine)
  implementation(libs.guice)
  implementation(libs.hash4j)
  implementation(libs.kubernetesClient)
  implementation(libs.kubernetesClientApi)
  implementation(libs.okHttp)
  api(project(":wisp:wisp-lease"))
  implementation(project(":misk-logging"))
  implementation(project(":misk-backoff"))
  implementation(project(":misk-lease"))
  implementation(project(":misk-service"))
  api(project(":misk-testing-api"))

  testImplementation(libs.apacheCommonsMath3)
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(project(":misk-clustering"))
  testImplementation(project(":misk-testing"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
