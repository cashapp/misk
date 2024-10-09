import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  id("java-test-fixtures")
}

dependencies {
  api(project(":misk-inject"))
  api(libs.prometheusClient)
  implementation(libs.findbugsJsr305)
  implementation(libs.guava)
  implementation(libs.guice)
  implementation(libs.jakartaInject)
  implementation(libs.kotlinStdLibJdk8)

  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-testing-api"))
  testFixturesApi(libs.prometheusClient)
  testFixturesImplementation(libs.guava)
  testFixturesImplementation(libs.guice)
  testFixturesImplementation(libs.kotlinStdLibJdk8)
  testFixturesImplementation(libs.micrometerCore)
  testFixturesImplementation(libs.micrometerRegistryPrometheus)

  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-metrics"))
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)

}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
