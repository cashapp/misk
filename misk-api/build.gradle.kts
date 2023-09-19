import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Avoid adding anything here, this module should be as self-contained as possible.
  api(Dependencies.okio)
  api(Dependencies.okHttp)
  implementation(kotlin("reflect"))

  // Will be removed with task https://ccp-cashapp.atlassian.net/browse/CCPOKR-9336
  api(Dependencies.jettyServletApi)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
