package misk.gradle.schemamigrator

import jakarta.inject.Inject
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional

abstract class SchemaMigratorExtension @Inject constructor(objects: ObjectFactory) {
  @get:Input abstract val database: Property<String>

  @get:Input @get:Optional abstract val host: Property<String>

  @get:Input @get:Optional abstract val port: Property<Int>

  @get:Input val databaseType: Property<String> = objects.property(String::class.java).convention("MYSQL")

  @get:Input abstract val username: Property<String>

  @get:Input abstract val password: Property<String>

  @get:InputDirectory abstract val migrationsDir: DirectoryProperty

  @get:Input val migrationsFormat: Property<String> = objects.property(String::class.java).convention("TRADITIONAL")
}
