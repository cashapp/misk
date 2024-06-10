import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  `java-gradle-plugin`
  id("com.vanniktech.maven.publish.base")
  `java-test-fixtures`
}

gradlePlugin {
  plugins {
    create("MiskSchemaMigratorPlugin") {
      id = "misk.schema-migrator"
      implementationClass = "misk.schemamigratorgradleplugin.MiskSchemaMigratorPlugin"
    }
  }
}

dependencies {
  api(project(":misk-inject"))
  api(libs.jakartaInject)

  implementation(project(":misk"))
  implementation(project(":misk-jdbc"))
  implementation(project(":misk-config"))
  implementation(project(":misk-service"))
  implementation(project(":wisp:wisp-deployment"))
  implementation(libs.guava)
  implementation(libs.guice)

  testImplementation(gradleTestKit())
  testImplementation(libs.assertj)
  testImplementation(libs.junitApi)
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
