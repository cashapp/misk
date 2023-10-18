import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  kotlin("jvm")
  `java-library`
}

dependencies {
  implementation(Dependencies.guice)
  implementation("com.github.jnr:jnr-ffi:2.2.11")
  implementation(platform(Dependencies.openRewriteBom))
  implementation(Dependencies.openRewriteJava)
  implementation(Dependencies.openRewriteJava)
  implementation(Dependencies.openRewriteKotlin)
  implementation(Dependencies.openRewriteProtobuf)

  runtimeOnly(Dependencies.openRewriteJava17)

  testImplementation(Dependencies.assertj)
  testImplementation(Dependencies.junitApi)
  testImplementation(Dependencies.junitParams)
  testImplementation(Dependencies.kotlinTest)
  testImplementation(Dependencies.openRewriteTest)
  testImplementation(Dependencies.openRewriteJavaTck)

  testRuntimeOnly(Dependencies.junitEngine)
}

