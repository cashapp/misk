import com.vanniktech.maven.publish.JavadocJar.Dokka
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
}

dependencies {
  api(libs.moshiCore)
  api(libs.dockerCore)
  implementation(libs.moshiKotlin)
  implementation(libs.okio)

  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
  testImplementation(libs.assertj)
  testImplementation(project(":misk-testing"))
  testImplementation(libs.okioFakefilesystem)
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = Dokka("dokkaGfm"))
  )
}

tasks.withType<Test> {
  // Makes fake docker credential scripts available to tests
  val fakeBinPath = File("${projectDir}/src/test/resources").absolutePath
  environment("PATH", fakeBinPath + File.pathSeparator + System.getenv("PATH"))
}
