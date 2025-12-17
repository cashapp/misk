package misk.vitess.gradle

import misk.vitess.testing.TransactionIsolationLevel
import misk.vitess.testing.VitessTestDb
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.net.URLClassLoader
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
  abstract val enableInMemoryStorage: Property<Boolean>

  @get:Input
  abstract val enableScatters: Property<Boolean>

  @get:Input
  abstract val inMemoryStorageSize: Property<String>

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

  @get:Classpath
  abstract val classpath: ConfigurableFileCollection

  @TaskAction
  fun start() {
    // Save the original ClassLoader
    val originalClassLoader = Thread.currentThread().contextClassLoader

    try {
      // Set up custom ClassLoader if classpath is provided
      if (!classpath.isEmpty) {
        val urls = classpath.files.map { it.toURI().toURL() }.toTypedArray()
        val customLoader = URLClassLoader(urls, originalClassLoader)
        Thread.currentThread().contextClassLoader = customLoader

        if (debugStartup.get()) {
          logger.info("StartVitessDatabaseTask: Using custom classpath with ${urls.size} entries")
          urls.forEachIndexed { index, url ->
            logger.info("  [$index]: $url")
          }
        }
      }

      val vitessTestDb = VitessTestDb(
        autoApplySchemaChanges = autoApplySchemaChanges.get(),
        containerName = containerName.get(),
        debugStartup = debugStartup.get(),
        enableDeclarativeSchemaChanges = enableDeclarativeSchemaChanges.get(),
        enableInMemoryStorage = enableInMemoryStorage.get(),
        enableScatters = enableScatters.get(),
        inMemoryStorageSize = inMemoryStorageSize.get(),
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
    } finally {
      // Always restore the original ClassLoader
      Thread.currentThread().contextClassLoader = originalClassLoader
    }
  }
}
