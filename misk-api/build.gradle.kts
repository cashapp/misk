import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Avoid adding dependencies on other misk modules here, this module should be as self-contained as possible.
  api(libs.okHttp)
  compileOnly(libs.jetbrainsAnnotations)
  compileOnly(libs.kotlinReflect)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
