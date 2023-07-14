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
  api(project(":misk:misk"))
  api(project(":misk:misk-hibernate"))
  api(project(":misk:misk-inject"))
  api(project(":misk:misk-jobqueue"))
  api(project(":misk:misk-transactional-jobqueue"))
  implementation(Dependencies.guava)
  implementation(project(":misk:misk-core"))
  implementation(project(":misk:misk-service"))
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
