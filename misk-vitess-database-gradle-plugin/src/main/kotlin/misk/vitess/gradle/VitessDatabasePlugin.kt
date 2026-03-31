package misk.vitess.gradle

import misk.vitess.testing.DefaultSettings
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.testing.Test

class VitessDatabasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val startVitessDatabase = project.tasks.register("startVitessDatabase", StartVitessDatabaseTask::class.java) {
      it.autoApplySchemaChanges.convention(DefaultSettings.AUTO_APPLY_SCHEMA_CHANGES)
      it.containerName.convention(DefaultSettings.CONTAINER_NAME)
      it.debugStartup.convention(DefaultSettings.DEBUG_STARTUP)
      it.enableDeclarativeSchemaChanges.convention(DefaultSettings.ENABLE_DECLARATIVE_SCHEMA_CHANGES)
      it.enableInMemoryStorage.convention(DefaultSettings.ENABLE_IN_MEMORY_STORAGE)
      it.enableScatters.convention(DefaultSettings.ENABLE_SCATTERS)
      it.inMemoryStorageSize.convention(DefaultSettings.IN_MEMORY_STORAGE_SIZE)
      it.keepAlive.convention(DefaultSettings.KEEP_ALIVE)
      it.lintSchema.convention(DefaultSettings.LINT_SCHEMA)
      it.mysqlVersion.convention(DefaultSettings.MYSQL_VERSION)
      it.port.convention(DefaultSettings.PORT)
      it.schemaDir.convention(
        "filesystem:${project.layout.projectDirectory.dir("src/main/resources/vitess/schema").asFile.absolutePath}"
      )
      it.sqlMode.convention(DefaultSettings.SQL_MODE)
      it.transactionIsolationLevel.convention(DefaultSettings.TRANSACTION_ISOLATION_LEVEL)
      it.transactionMode.convention(DefaultSettings.TRANSACTION_MODE)
      it.transactionTimeoutSeconds.convention(DefaultSettings.TRANSACTION_TIMEOUT_SECONDS)
      it.vitessImage.convention(DefaultSettings.VITESS_IMAGE)
      it.vitessVersion.convention(DefaultSettings.VITESS_VERSION)
    }

    // Forward StartVitessDatabaseTask @Input properties to Test tasks so that changes
    // to Vitess configuration (e.g. enableScatters) invalidate the test cache.
    // Without this, StartVitessDatabaseTask's @UntrackedTask annotation means its
    // properties don't participate in any cache fingerprint, so downstream tests
    // can be served from cache even when the Vitess config has changed.
    val inputGetters = StartVitessDatabaseTask::class.java.methods
      .filter { it.isAnnotationPresent(Input::class.java) }

    project.tasks.withType(Test::class.java).configureEach { test ->
      for (getter in inputGetters) {
        val name = getter.name.removePrefix("get").replaceFirstChar { it.lowercase() }
        @Suppress("UNCHECKED_CAST")
        test.inputs.property(
          "vitess.$name",
          startVitessDatabase.flatMap { getter.invoke(it) as Provider<Any> }
        )
      }
    }
  }
}
