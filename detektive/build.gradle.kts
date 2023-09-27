import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(Dependencies.detektApi)
  compileOnly(Dependencies.detektPsiUtils)
  compileOnly(Dependencies.kotlinCompilerEmbeddable)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.detektTest)
  testImplementation(Dependencies.detektTestUtils)
  testImplementation(Dependencies.guice)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)

  testRuntimeOnly(Dependencies.junitEngine)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
