import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-gradle-plugin")
}

gradlePlugin {
  plugins {
    create("VitessDatabasePlugin") {
      id = "com.squareup.misk.vitess.vitess-database"
      implementationClass = "misk.vitess.gradle.VitessDatabasePlugin"
    }
  }
}

dependencies {
  implementation(testFixtures(project(":misk-vitess")))

  testImplementation(gradleTestKit())
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testImplementation(libs.mysql)
}

mavenPublishing {
  configure(
    GradlePlugin(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
