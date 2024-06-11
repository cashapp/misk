import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  `java-gradle-plugin`
}

gradlePlugin {
  plugins {
    create("MiskSchemaMigratorPlugin") {
      id = "misk.schema-migrator"
      implementationClass = "misk.gradle.schemamigrator.SchemaMigratorPlugin"
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

mavenPublishing {
  configure(
    GradlePlugin(javadocJar = JavadocJar.None())
  )
}
