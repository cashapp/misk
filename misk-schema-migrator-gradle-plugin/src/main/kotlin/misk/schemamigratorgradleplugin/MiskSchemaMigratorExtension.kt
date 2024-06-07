package misk.schemamigratorgradleplugin

import jakarta.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory

abstract class MiskSchemaMigratorExtension @Inject constructor(objects: ObjectFactory) {
  @get:Input
  abstract val database: Property<String>

  @get:Input
  val databaseType: Property<String> = objects.property(String::class.java).convention("MYSQL")

  @get:Input
  abstract val username: Property<String>

  @get:Input
  abstract val password: Property<String>

  @get:InputDirectory
  abstract val migrationsDir: DirectoryProperty
}
