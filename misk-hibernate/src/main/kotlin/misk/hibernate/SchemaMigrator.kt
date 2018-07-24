package misk.hibernate

import com.google.common.base.Stopwatch
import misk.logging.getLogger
import misk.resources.ResourceLoader
import org.hibernate.SessionFactory
import org.hibernate.query.Query
import java.sql.Connection
import java.util.*
import java.util.regex.Pattern
import javax.persistence.PersistenceException
import kotlin.reflect.KClass

private val logger = getLogger<SchemaMigrator>()

/**
 * Manages **available** and **applied** schema migrations.
 *
 * Available schema migrations are SQL files in the datasource's `migrations_resource` directory.
 * Each file should contain SQL statements terminated by a `;`. The files should be named like
 * `v100__exemplar.sql` with a `v`, an integer version, two underscores, a description, and the
 * `.sql` suffix. The integer identifier is the migration version. Versions do not need to be
 * sequential. They are applied in increasing order.
 *
 * Applied schema migrations are tracked in the database in a `schema_version` table. Migrations may
 * be applied either by [SchemaMigrator.applyAll] or manually. When you applying schema changes
 * manually you must add a row to the `schema_version` table to record which version was applied.
 */
internal class SchemaMigrator(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val sessionFactory: SessionFactory,
  private val config: DataSourceConfig
) {
  /** Returns a map from version to path. */
  fun availableMigrations(): NavigableMap<Int, String> {
    val resources = config.migrations_resource?.let { resourceLoader.list(it) } ?: listOf()
    return TreeMap(resources.associateBy { resourceVersion(it) })
  }

  /** Creates the `schema_version` table if it does not exist. Returns the applied migrations. */
  fun initialize(): NavigableSet<Int> {
    if (config.migrations_resource == null) {
      return TreeSet()
    }
    try {
      val result = appliedMigrations()
      logger.info {
        "${qualifier.simpleName} has ${result.size} migrations applied;" +
            " latest is ${result.lastOrNull()}"
      }
      return result
    } catch (e: PersistenceException) {
      sessionFactory.doWork {
        val statement = createStatement()
        statement.addBatch("""
            |CREATE TABLE schema_version (
            |  version varchar(50) NOT NULL,
            |  installed_by varchar(30) DEFAULT NULL,
            |  UNIQUE KEY (version)
            |);
            |""".trimMargin())
        statement.executeBatch()
      }
      logger.info {
        "${qualifier.simpleName} has no migrations applied; created the schema_version table"
      }
      return Collections.emptyNavigableSet()
    }
  }

  /**
   * Returns the versions of applied migrations. Throws a [PersistenceException] if the migrations
   * table has not been initialized.
   */
  fun appliedMigrations(): NavigableSet<Int> {
    sessionFactory.openSession().use { session ->
      @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
      val query = session.createNativeQuery("SELECT version FROM schema_version") as Query<String>
      return TreeSet(query.list().map { it.toInt() })
    }
  }

  /** Applies all available migrations that haven't yet been applied. */
  fun applyAll(author: String, appliedVersions: Set<Int>) {
    require(author.matches(Regex("\\w+"))) // Prevent SQL injection.

    val availableMigrations = availableMigrations()

    for ((version, path) in availableMigrations) {
      if (appliedVersions.contains(version)) continue

      val migrationSql = resourceLoader.utf8(path)
      val stopwatch = Stopwatch.createStarted()

      sessionFactory.doWork {
        val migration = createStatement()
        migration.addBatch(migrationSql)
        migration.executeBatch()

        val schemaVersion = prepareStatement("""
            |INSERT INTO schema_version (version, installed_by) VALUES (?, ?);
            |""".trimMargin())
        schemaVersion.setInt(1, version)
        schemaVersion.setString(2, author)
        schemaVersion.executeUpdate()
      }

      logger.info { "${qualifier.simpleName} applied $path in $stopwatch" }
    }
  }

  /** Throws an exception unless all available migrations have been applied. */
  fun requireAll() {
    try {
      val availableMigrations = availableMigrations()
      val appliedMigrations = appliedMigrations()

      val missing = mutableListOf<String>()
      for ((version, path) in availableMigrations) {
        if (!appliedMigrations.contains(version)) {
          missing += path
        }
      }

      check(missing.isEmpty()) {
        "${qualifier.simpleName} is missing migrations:\n  " + missing.joinToString(separator = "\n  ")
      }
    } catch (e: PersistenceException) {
      throw IllegalStateException("${qualifier.simpleName} is not ready", e)
    }
  }

  internal fun resourceVersion(resource: String): Int {
    val matcher = RESOURCE_PATTERN.matcher(resource)
    require(matcher.matches()) { "unexpected resource: $resource" }
    return matcher.group(1).toInt()
  }

  private fun <R> SessionFactory.doWork(lambda: Connection.() -> R): R {
    openSession().use { session ->
      return session.doReturningWork { connection ->
        connection.lambda()
      }
    }
  }

  companion object {
    /** Matches file names like `exemplar/migrations/v100__exemplar.sql`. */
    val RESOURCE_PATTERN = Pattern.compile("(?:.*/?)v(\\d+)__[^/]+\\.sql")!!
  }
}
