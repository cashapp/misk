import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(Dependencies.guice)
  api(Dependencies.javaxInject)
  api(Dependencies.wispToken)
  api(project(":misk"))
  api(project(":misk-hibernate"))
  api(project(":misk-inject"))
  api(project(":misk-jobqueue"))
  api(project(":misk-transactional-jobqueue"))
  implementation(project(":misk-core"))
  implementation(project(":misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
