package misk.gradle.schemamigrator

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import java.time.Duration
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

class SchemaMigratorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = create(project)

    project.tasks.register(SchemaMigratorTask.NAME, SchemaMigratorTask::class.java) {
      it.database.set(extension.database)
      it.host.set(extension.host)
      it.port.set(extension.port)
      it.databaseType.set(extension.databaseType)
      it.username.set(extension.username)
      it.password.set(extension.password)
      it.migrationsDir.set(extension.migrationsDir)
      it.migrationsFormat.set(extension.migrationsFormat)
    }
  }

  internal companion object {
    fun create(project: Project): SchemaMigratorExtension {
      return project.extensions.create("miskSchemaMigrator", SchemaMigratorExtension::class.java)
    }
  }
}

abstract class SchemaMigratorTask : DefaultTask() {

  companion object {
    // Deliberately exposed as a public property for use by other plugins
    const val NAME = "migrateSchema"
  }

  @get:Input abstract val database: Property<String>

  @get:Input @get:Optional abstract val host: Property<String>

  @get:Input @get:Optional abstract val port: Property<Int>

  @get:Input abstract val databaseType: Property<String>

  @get:Input abstract val username: Property<String>

  @get:Input abstract val password: Property<String>

  @get:PathSensitive(PathSensitivity.RELATIVE) @get:InputDirectory abstract val migrationsDir: DirectoryProperty

  @get:Input @get:Optional abstract val migrationsFormat: Property<String>

  @TaskAction
  fun migrateSchemas() {
    val injector =
      Guice.createInjector(
        SchemaMigratorModule(
          database.get(),
          host.orNull,
          port.orNull,
          databaseType.get(),
          username.get(),
          password.get(),
          migrationsDir.asFile.get(),
          migrationsFormat.get(),
        )
      )

    val serviceManager = injector.getInstance(ServiceManager::class.java)

    serviceManager.startAsync().awaitHealthy()
    serviceManager.stopAsync().awaitStopped(Duration.ofSeconds(5))
  }
}
