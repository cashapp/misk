package misk.gradle.schemamigrator

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

class SchemaMigratorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = create(project)

    project.tasks.register("migrateSchema", SchemaMigratorTask::class.java) {
      it.database.set(extension.database)
      it.databaseType.set(extension.databaseType)
      it.username.set(extension.username)
      it.password.set(extension.password)
      it.migrationsDir.set(extension.migrationsDir)
    }
  }

  internal companion object {
    fun create(project: Project): SchemaMigratorExtension {
      return project.extensions.create("miskSchemaMigrator", SchemaMigratorExtension::class.java)
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

  @get:PathSensitive(PathSensitivity.RELATIVE)
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
