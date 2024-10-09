import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.guava)
  api(libs.guice)
  api(libs.jakartaInject)
  api(project(":misk-api"))
  api(project(":misk-inject"))
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLibJdk8)
  implementation(libs.kotlinxCoroutinesCore)
  implementation(libs.moshiCore)

  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.kotlinTest)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
