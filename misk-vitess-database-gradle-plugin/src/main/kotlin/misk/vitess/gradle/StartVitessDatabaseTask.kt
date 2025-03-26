package misk.vitess.gradle

import misk.vitess.testing.TransactionIsolationLevel
import misk.vitess.testing.VitessTestDb
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.time.Duration

@UntrackedTask(because = "Every task run re-initializes the database if needed")
abstract class StartVitessDatabaseTask: DefaultTask() {

  @get:Input
  abstract val autoApplySchemaChanges: Property<Boolean>

  @get:Input
  abstract val containerName: Property<String>

  @get:Input
  abstract val debugStartup: Property<Boolean>

  @get:Input
  abstract val enableDeclarativeSchemaChanges: Property<Boolean>

  @get:Input
  abstract val enableScatters: Property<Boolean>

  @get:Input
  abstract val keepAlive: Property<Boolean>

  @get:Input
  abstract val lintSchema: Property<Boolean>

  @get:Input
  abstract val mysqlVersion: Property<String>

  @get:Input
  abstract val port: Property<Int>

  @get:Input
  abstract val schemaDir: Property<String>

  @get:Input
  abstract val sqlMode: Property<String>

  @get:Input
  abstract val transactionIsolationLevel: Property<TransactionIsolationLevel>

  @get:Input
  abstract val transactionTimeoutSeconds: Property<Duration>

  @get:Input
  abstract val vitessImage: Property<String>

  @get:Input
  abstract val vitessVersion: Property<Int>

  @TaskAction
  fun start() {
    val vitessTestDb = VitessTestDb(
      autoApplySchemaChanges = autoApplySchemaChanges.get(),
      containerName = containerName.get(),
      debugStartup = debugStartup.get(),
      enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges.get(),
      enableScatters = enableScatters.get(),
      keepAlive = keepAlive.get(),
      lintSchema = lintSchema.get(),
      mysqlVersion = mysqlVersion.get(),
      port = port.get(),
      schemaDir = schemaDir.get(),
      sqlMode = sqlMode.get(),
      transactionIsolationLevel = transactionIsolationLevel.get(),
      transactionTimeoutSeconds = transactionTimeoutSeconds.get(),
      vitessImage = vitessImage.get(),
      vitessVersion = vitessVersion.get()
    )

    vitessTestDb.run()
  }
}
