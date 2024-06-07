package misk.schemamigratorgradleplugin

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register

class MiskSchemaMigratorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("miskSchemaMigrator", MiskSchemaMigratorExtension::class.java)

    project.tasks.register<SchemaMigratorTask>("migrateSchema") {
      database.set(extension.database)
      databaseType.set(extension.databaseType)
      username.set(extension.username)
      password.set(extension.password)
      migrationsDir.set(extension.migrationsDir)
    }
  }
}

abstract class SchemaMigratorTask : DefaultTask() {
  @get:Input
  abstract val database: Property<String>

  @get:Input
  abstract val databaseType: Property<String>

  @get:Input
  abstract val username: Property<String>

  @get:Input
  abstract val password: Property<String>

  @get:InputDirectory
  abstract val migrationsDir: DirectoryProperty

  @TaskAction
  fun migrateSchemas() {
    val injector = Guice.createInjector(
      SchemaMigratorModule(
        database.get(),
        databaseType.get(),
        username.get(),
        password.get(),
        migrationsDir.asFile.get()
      )
    )

    val serviceManager = injector.getInstance(ServiceManager::class.java)
    serviceManager.startAsync()
    serviceManager.awaitHealthy()
  }
}
