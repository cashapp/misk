import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  // Avoid adding dependencies on other misk modules here, this module should be as self-contained as possible.
  api(libs.okHttp)
  compileOnly(libs.jetbrainsAnnotations)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}

tasks.getting(KotlinCompile::class) {
  compilerOptions {
    freeCompilerArgs.add("-Xjvm-default=all-compatibility")
  }
}
