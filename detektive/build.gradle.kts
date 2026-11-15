import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  compileOnly(libs.detektApi)

  testImplementation(libs.assertj)
  testImplementation(libs.kotlinCompilerForDetekt)
  testImplementation(libs.detektTest)
  testImplementation(libs.detektTestJunit)
  testImplementation(libs.detektTestUtils)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)

  testRuntimeOnly(libs.junitEngine)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationMarkdown"))
  )
}
