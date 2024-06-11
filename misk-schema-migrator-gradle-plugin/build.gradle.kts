import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.mavenPublishBase)
  `java-gradle-plugin`
  `java-test-fixtures`
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

publishing {
  publications {
    // Remove the default 'pluginMaven' publication to avoid conflicts
    withType<MavenPublication>().configureEach {
      if (name == "pluginMaven") {
        tasks.named("publishPluginMavenPublicationToMavenLocal").configure {
          enabled = false
        }
        tasks.named("publishPluginMavenPublicationToMavenCentralRepository").configure {
          enabled = false
        }
      }
    }
  }
}

mavenPublishing {
  configure(
    KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
