import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
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

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
