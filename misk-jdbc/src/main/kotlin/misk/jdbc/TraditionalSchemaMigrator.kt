package misk.jdbc

import com.google.common.base.Stopwatch
import misk.resources.ResourceLoader
import wisp.logging.getLogger
import java.sql.Connection
import java.sql.SQLException
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.regex.Pattern
import kotlin.reflect.KClass

private val logger = getLogger<TraditionalSchemaMigrator>()

internal data class NamedspacedMigration(
  val version: Int,
  val namespace: String = "",
) : Comparable<NamedspacedMigration> {
  // We don't store the path from which schema changes came, so we don't use it for comparison
  // between what's in the database and what's available.
  var path = ""

  override fun compareTo(other: NamedspacedMigration): Int {
    return if (namespace == other.namespace) {
      version.compareTo(other.version)
    } else {
      namespace.compareTo(other.namespace)
    }
  }

  fun toNamespacedVersion() = "$namespace$version"

  companion object {
    /**
     *  NamespacedVersions in the database can be either an "<int>" or "<namespace>/<int>", such as:
     *    - 1
     *    - syncengine/1
     *    - com/squareup/cash/client/2
     *   The namespace is the subdirectory structure in which the migration was found.
     */
    fun fromNamespacedVersion(namespacedVersion: String): NamedspacedMigration {
      return if (namespacedVersion.toIntOrNull() != null) {
        NamedspacedMigration(namespacedVersion.toInt())
      } else {
        val items = namespacedVersion.split(NAMESPACE_SEPARATOR)
        val version = items.last().toInt()
        val namespace = items.dropLast(1).joinToString(NAMESPACE_SEPARATOR) + NAMESPACE_SEPARATOR
        NamedspacedMigration(version, namespace)
      }
    }

    /**
     * Given a resource from, parse out the version and namespace. For example:
     *  - classpath:/migrations/v1_table.sql becomes "1"
     *  - classpath:/migrations/com/example/library/v1_table.sql becomes "com/example/library/1"
     */
    fun fromResourcePath(
      resource: String,
      migrationsResource: String,
      migrationPattern: String,
    ): NamedspacedMigration {
      val matcher = Pattern.compile(migrationPattern).matcher(resource)
      require(matcher.matches()) { "unexpected resource: $resource" }
      val cleanNamespace = matcher.group(1).removePrefix(migrationsResource).removePrefix("/")
      val namedspacedMigration = NamedspacedMigration(matcher.group(2).toInt(), cleanNamespace)
      namedspacedMigration.path = resource
      return namedspacedMigration
    }

    private const val NAMESPACE_SEPARATOR = "/"
    const val DECLARATIVE_MIGRATIONS_SUFFIX = "-skeemaautoversion"
  }
}

/**
 * Manages **available** and **applied** schema migrations.
 *
 * Available schema migrations are SQL files in the datasource's `migrations_resource` directory.
 * Multiple directories can be specified in `migrations_resources` to support dependencies with
 * their own migrations.
 *
 * Each file should contain SQL statements terminated by a `;`. The files should be named like
 * `v100__exemplar.sql` with a `v`, an integer version, two underscores, a description, and the
 * `.sql` suffix. The integer identifier is the migration version. Versions do not need to be
 * sequential. They are applied in increasing order.
 *
 * Migrations found in subdirectories will become namespaced, where the namespace is the directory
 * path. This allows including migrations from dependencies. Migrations in the root directory have
 * versions "1", "2", etc. Namespaced migrations have versions in the form of "dir/1", "dir/sub/1"
 *
 * Applied schema migrations are tracked in the database in a `schema_version` table. Migrations may
 * be applied either by [TraditionalSchemaMigrator.applyAll] or manually. When you applying schema changes
 * manually you must add a row to the `schema_version` table to record which version was applied.
 */
internal class TraditionalSchemaMigrator(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val dataSourceConfig: DataSourceConfig,
  private val dataSourceService: DataSourceService,
  private val connector: DataSourceConnector,
) : BaseSchemaMigrator(resourceLoader, dataSourceService) {

  override fun validateMigrationFile(migrationFile: MigrationFile): Boolean {
    return Pattern.compile(connector.config().migrations_resources_regex).matcher(migrationFile.filename).matches()
  }

  /** Returns a SortedSet of all migrations found in the source directories and subdirectories. */
  fun availableMigrations(): SortedSet<NamedspacedMigration> {
    val migrations = getMigrationFiles().map {
      NamedspacedMigration.fromResourcePath(it.filename, it.resource, connector.config().migrations_resources_regex)
    }

    val migrationMap = TreeMap<NamedspacedMigration, MutableList<NamedspacedMigration>>()

    migrations.forEach {
      val previousValue = migrationMap[it]

      if (previousValue == null) {
        migrationMap[it] = mutableListOf(it)
      } else {
        previousValue.add(it)
      }
    }

    val duplicates = migrationMap.values.filter { it.size > 1 }
    require(duplicates.isEmpty()) { "Duplicate migrations found $duplicates" }
    return migrationMap.navigableKeySet()
  }

  override fun applyAll(author: String) : MigrationStatus {
    if (connector.config().type.isVitess) {
      // VitessTestDb handles applying traditional schema changes.
      throw UnsupportedOperationException("Traditional schema changes `applyAll` is not supported for Vitess in Misk.")
    }

    val appliedMigrations = initialize()
    return applyAll("SchemaMigratorService", appliedMigrations)
  }

  /** Creates the `schema_version` table if it does not exist. Returns the applied migrations. */
  fun initialize(): SortedSet<NamedspacedMigration> {
    try {
      val result = appliedMigrations()
      logger.info {
        "${qualifier.simpleName} has ${result.size} migrations applied;" +
          " latest is ${result.lastOrNull()}"
      }
      return result
    } catch (e: SQLException) {
      dataSourceService.dataSource.connection.use { c ->
        c.createStatement().use { statement ->
          statement.execute(
            """
                |CREATE TABLE schema_version (
                |  version varchar(50) PRIMARY KEY,
                |  installed_by varchar(30) DEFAULT NULL
                |);
                |""".trimMargin()
          )
        }
        c.createStatement().use { statement ->
          statement.execute("COMMIT")
        }
      }
      return sortedSetOf<NamedspacedMigration>()
    }
  }

  /**
   * Returns the versions of applied migrations. Throws a [java.sql.SQLException]
   * if the migrations table has not been initialized.
   */
  fun appliedMigrations(): SortedSet<NamedspacedMigration> {
    val listMigrations = { conn: Connection ->
      conn.createStatement().use { stmt ->
        val results = mutableSetOf<String>()
        stmt.executeQuery("SELECT version FROM schema_version").use { resultSet ->
          while (resultSet.next()) {
            results.add(resultSet.getString(1))
          }
        }
        results
          .filterNot { it.endsWith(NamedspacedMigration.DECLARATIVE_MIGRATIONS_SUFFIX) }
          .map { NamedspacedMigration.fromNamespacedVersion(it) }
          .toSortedSet()
      }
    }
      return dataSourceService.dataSource.connection.use {
        listMigrations(it)
      }
  }

  /** Applies all available migrations that haven't yet been applied. */
  fun applyAll(author: String, appliedMigrations: SortedSet<NamedspacedMigration>): MigrationState {
    require(author.matches(Regex("\\w+"))) // Prevent SQL injection.

    val availableMigrations = availableMigrations()
    val migrationState = MigrationState(availableMigrations, appliedMigrations)
    for (migration in migrationState.missingMigrations()) {
      val migrationSql = resourceLoader.utf8(migration.path)
      val stopwatch = Stopwatch.createStarted()

      dataSourceService.dataSource.connection.use { c ->
          c.createStatement().use { migrationStatement ->
            migrationStatement.addBatch(migrationSql)
            migrationStatement.executeBatch()
          }

          c.prepareStatement(
            """
          |INSERT INTO schema_version (version, installed_by) VALUES (?, ?);
          |""".trimMargin()
          ).use { schemaVersion ->
            schemaVersion.setString(1, migration.toNamespacedVersion())
            schemaVersion.setString(2, author)
            schemaVersion.executeUpdate()
          }

          c.commit()
        }

        logger.info { "${qualifier.simpleName} applied $migration in $stopwatch" }
      }

    // All available migrations are applied, so use availableMigrations for both properties.
    return MigrationState(availableMigrations, TreeSet(appliedMigrations + availableMigrations))
  }

  /** Throws an exception unless all available migrations have been applied. */
  override fun requireAll(): MigrationStatus {
    if (connector.config().type.isVitess) {
      throw UnsupportedOperationException("Traditional schema changes `requireAll` is not supported for Vitess in Misk.")
    }

    try {
      val availableMigrations = availableMigrations()
      val appliedMigrations = appliedMigrations()
      val state = MigrationState(availableMigrations, appliedMigrations)
      val missingMigrations = state.missingMigrations()

      check(missingMigrations.isEmpty()) {
        val qualifiedAppliedMigrations = availableMigrations - missingMigrations
        return@check """
	        |${qualifier.simpleName} is missing migrations:
          |  ${missingMigrations.joinToString(separator = "\n  ") { it.path }}
          |${qualifier.simpleName} has applied migrations:
          |  ${qualifiedAppliedMigrations.joinToString(separator = "\n  ") { it.path }}
          """.trimMargin()
      }

      return state
    } catch (e: SQLException) {
      throw IllegalStateException("${qualifier.simpleName} is not ready", e)
    }
  }
}

/** Snapshot of all shards in a cluster. */
internal data class MigrationState(
  val available: SortedSet<NamedspacedMigration>,
  val applied: SortedSet<NamedspacedMigration>,
) : MigrationStatus {
  fun missingMigrations() = available - applied

  override fun toString(): String {
    return if (available == applied) {
      "MigrationState(all " + available.size + " migrations applied)"
    } else {
      "MigrationState(not applied=${available - applied}, not tracked=${applied - available})"
    }
  }
}
