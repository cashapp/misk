package misk.gradle.schemamigrator

import java.io.ByteArrayInputStream
import java.io.File
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

class SchemaMigratorPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = create(project)

    val schemaMigratorClasspath = project.configurations.create("schemaMigratorClasspath") {
      it.isCanBeConsumed = false
      it.isCanBeResolved = true
      it.defaultDependencies { deps ->
        val version = loadDefaultVersion()
        deps.add(project.dependencies.create("com.squareup.misk:misk-jdbc:$version"))
      }
    }

    // Allow overriding the worker classpath via a Gradle property, e.g. for testing:
    //   -PschemaMigratorClasspath=/path/to/jar1:/path/to/jar2
    val classpathOverride = project.findProperty("schemaMigratorClasspath") as? String
    if (classpathOverride != null) {
      val files = classpathOverride.split(File.pathSeparator).filter { it.isNotBlank() }.map { project.file(it) }
      project.dependencies.add("schemaMigratorClasspath", project.files(files))
    }

    project.tasks.register(SchemaMigratorTask.NAME, SchemaMigratorTask::class.java) {
      it.workerClasspath.from(schemaMigratorClasspath.incoming.files)
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

    fun loadDefaultVersion(): String {
      val props = Properties()
      val stream = SchemaMigratorPlugin::class.java.classLoader
        .getResourceAsStream("misk-schema-migrator.properties")
        ?: error("misk-schema-migrator.properties not found in plugin classpath")
      props.load(stream)
      return props.getProperty("version")
        ?: error("version property not found in misk-schema-migrator.properties")
    }
  }
}

abstract class SchemaMigratorTask : DefaultTask() {

  companion object {
    const val NAME = "migrateSchema"
  }

  @get:Inject abstract val execOperations: ExecOperations

  @get:Classpath abstract val workerClasspath: ConfigurableFileCollection

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
    val props = buildString {
      appendLine("database=${database.get()}")
      appendLine("host=${host.orNull.orEmpty()}")
      appendLine("port=${port.orNull?.toString().orEmpty()}")
      appendLine("databaseType=${databaseType.get()}")
      appendLine("username=${username.get()}")
      appendLine("password=${password.get()}")
      appendLine("migrationsResource=filesystem:${migrationsDir.get().asFile}")
      appendLine("migrationsFormat=${migrationsFormat.get()}")
    }

    execOperations.javaexec {
      it.classpath = workerClasspath
      it.mainClass.set("misk.jdbc.SchemaMigratorRunner")
      it.standardInput = ByteArrayInputStream(props.toByteArray())
    }.assertNormalExitValue()
  }
}
