import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
