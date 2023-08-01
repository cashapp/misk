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
  api(Dependencies.prometheusClient)
  implementation(Dependencies.findBugs)
  implementation(Dependencies.guava)
  implementation(Dependencies.kotlinStdLibJdk8)

  testFixturesApi(Dependencies.prometheusClient)
  testFixturesApi(project(":misk-inject"))
  testFixturesApi(project(":misk-metrics"))
  testFixturesImplementation(Dependencies.guava)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinStdLibJdk8)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.jakartaInject)
  testImplementation(Dependencies.junitApi)
  testImplementation(project(":misk-metrics"))
  testImplementation(project(":misk-testing"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
