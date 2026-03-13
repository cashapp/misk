import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish.base")
  id("java-gradle-plugin")
}

gradlePlugin {
  plugins {
    create("MiskSchemaMigratorPlugin") {
      id = "com.squareup.misk.schema-migrator"
      implementationClass = "misk.gradle.schemamigrator.SchemaMigratorPlugin"
    }
  }
}

val generateVersionProperties by tasks.registering {
  val outputDir = layout.buildDirectory.dir("generated-resources")
  val version = project.version.toString()
  inputs.property("version", version)
  outputs.dir(outputDir)
  doLast {
    val file = outputDir.get().asFile.resolve("misk-schema-migrator.properties")
    file.parentFile.mkdirs()
    file.writeText("version=$version\n")
  }
}

sourceSets.main {
  resources.srcDir(generateVersionProperties)
}

dependencies {
  testImplementation(gradleTestKit())
  testImplementation(libs.assertj)
  testImplementation(libs.hikariCp)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitParams)
  testRuntimeOnly(project(":misk-jdbc"))
  testRuntimeOnly(libs.mysql)
}

mavenPublishing {
  configure(
    GradlePlugin(javadocJar = JavadocJar.Dokka("dokkaGfm"))
  )
}
