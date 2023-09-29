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
  api(Dependencies.prometheusClient)
  implementation(Dependencies.jakartaInject)
  implementation(Dependencies.findBugs)
  implementation(Dependencies.guava)
  implementation(Dependencies.guice)
  implementation(Dependencies.kotlinStdLibJdk8)

  testFixturesApi(project(":misk-inject"))
  testFixturesApi(Dependencies.prometheusClient)
  testFixturesImplementation(Dependencies.guava)
  testFixturesImplementation(Dependencies.guice)
  testFixturesImplementation(Dependencies.kotlinStdLibJdk8)
  testFixturesImplementation(Dependencies.micrometerCore)
  testFixturesImplementation(Dependencies.micrometerPrometheus)

  testImplementation(project(":misk-testing"))
  testImplementation(project(":misk-metrics"))
  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)


}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
