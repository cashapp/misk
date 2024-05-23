import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
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

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
