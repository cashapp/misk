import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
}

dependencies {
  api(libs.kotlinXHtml)
  api(project(":wisp:wisp-deployment"))
  implementation(project(":misk-hotwire"))
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}
