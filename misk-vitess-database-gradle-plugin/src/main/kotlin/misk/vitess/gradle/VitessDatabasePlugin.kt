package misk.vitess.gradle

import misk.vitess.testing.DefaultSettings
import org.gradle.api.Plugin
import org.gradle.api.Project

class VitessDatabasePlugin: Plugin<Project> {
  override fun apply(project: Project) {
    project.tasks.register("startVitessDatabase", StartVitessDatabaseTask::class.java) {
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
      it.schemaDir.convention("filesystem:${project.layout.projectDirectory.dir("src/main/resources/vitess/schema").asFile.absolutePath}")
      it.sqlMode.convention(DefaultSettings.SQL_MODE)
      it.transactionIsolationLevel.convention(DefaultSettings.TRANSACTION_ISOLATION_LEVEL)
      it.transactionTimeoutSeconds.convention(DefaultSettings.TRANSACTION_TIMEOUT_SECONDS)
      it.vitessImage.convention(DefaultSettings.VITESS_IMAGE)
      it.vitessVersion.convention(DefaultSettings.VITESS_VERSION)
    }
  }
}
