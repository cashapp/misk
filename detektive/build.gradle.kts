import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(libs.detektApi)
  compileOnly(libs.detektPsiUtils)
  compileOnly(libs.kotlinCompilerEmbeddable)

  testImplementation(libs.assertj)
  testImplementation(libs.detektParser)
  testImplementation(libs.detektTest)
  testImplementation(libs.detektTestUtils)
  testImplementation(libs.guice)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.kotlinTest)

  testRuntimeOnly(libs.junitEngine)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
