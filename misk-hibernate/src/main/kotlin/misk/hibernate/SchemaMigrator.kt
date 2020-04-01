package misk.hibernate

import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Stopwatch
import com.google.common.collect.ImmutableList
import misk.jdbc.DataSourceConnector
import misk.logging.getLogger
import misk.resources.ResourceLoader
import org.hibernate.query.Query
import java.util.SortedSet
import java.util.TreeSet
import java.util.regex.Pattern
import javax.inject.Provider
import javax.persistence.PersistenceException
import kotlin.reflect.KClass

private val logger = getLogger<SchemaMigrator>()

internal data class NamedspacedMigration(
  val version: Int,
  val namespace: String = ""
) : Comparable<NamedspacedMigration> {
  // We don't store the path from which schema changes came, so we don't use it for comparison
  // between what's in the database and what's available.
  var path = ""

  override fun compareTo(other: NamedspacedMigration): Int {
    if (namespace == other.namespace) {
      return version.compareTo(other.version)
    } else {
      return namespace.compareTo(other.namespace)
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
      if (namespacedVersion.toIntOrNull() != null) {
        return NamedspacedMigration(namespacedVersion.toInt())
      } else {
        val items = namespacedVersion.split("/")
        val version = items.last().toInt()
        val namespace = items.dropLast(1).joinToString("/") + "/"
        return NamedspacedMigration(version, namespace)
      }
    }

    /**
     * Given a resource from, parse out the version and namespace. For example:
     *  - classpath:/migrations/v1_table.sql becomes "1"
     *  - classpath:/migrations/com/example/library/v1_table.sql becomes "com/example/library/1"
     */
    fun fromResourcePath(resource: String, migrationsResource: String): NamedspacedMigration {
      val matcher = MIGRATION_PATTERN.matcher(resource)
      require(matcher.matches()) { "unexpected resource: $resource" }
      val cleanNamespace = matcher.group(1).removePrefix(migrationsResource).removePrefix("/")
      val namedspacedMigration = NamedspacedMigration(matcher.group(2).toInt(), cleanNamespace)
      namedspacedMigration.path = resource
      return namedspacedMigration
    }

    /** Matches file names like `exemplar/migrations/v100__exemplar.sql`. */
    val MIGRATION_PATTERN = Pattern.compile("(^|.*/)v(\\d+)__[^/]+\\.sql")!!
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
 * be applied either by [SchemaMigrator.applyAll] or manually. When you applying schema changes
 * manually you must add a row to the `schema_version` table to record which version was applied.
 */
internal class SchemaMigrator(
  private val qualifier: KClass<out Annotation>,
  private val resourceLoader: ResourceLoader,
  private val transacter: Provider<Transacter>,
  private val connector: DataSourceConnector
) {

  private fun getMigrationsResources(keyspace: Keyspace): List<String> {
    val config = connector.config()
    val migrationsResources = ImmutableList.builder<String>()
    if (config.migrations_resource != null) {
      migrationsResources.add(config.migrations_resource)
    }
    if (config.migrations_resources != null) {
      migrationsResources.addAll(config.migrations_resources)
    }
    if (config.vitess_schema_resource_root != null) {
      migrationsResources.add(config.vitess_schema_resource_root + "/" + keyspace.name)
    }
    return migrationsResources.build()
  }

  /** Returns a SortedSet of all migrations found in the source directories and subdirectories. */
  fun availableMigrations(keyspace: Keyspace): SortedSet<NamedspacedMigration> {
    val migrations = mutableListOf<NamedspacedMigration>()
    for (migrationsResource in getMigrationsResources(keyspace)) {
      val migrationsFound = resourceLoader.walk(migrationsResource).filter { it.endsWith(".sql") }
          .map { NamedspacedMigration.fromResourcePath(it, migrationsResource) }
      migrations.addAll(migrationsFound)
    }
    migrations.toSortedSet().let {
      require(it.size == migrations.size) { "Duplicate migrations found $migrations" }
      return it
    }
  }

  /** Creates the `schema_version` table if it does not exist. Returns the applied migrations. */
  fun initialize(): SortedSet<NamedspacedMigration> {
    val noMigrations =
        transacter.get().shards().all { shard -> getMigrationsResources(shard.keyspace).isEmpty() }
    if (noMigrations) {
      return sortedSetOf()
    }
    return transacter.get().shards().flatMapTo(TreeSet()) { shard ->
      try {
        val result = appliedMigrations(shard)
        logger.info {
          "${qualifier.simpleName} has ${result.size} migrations applied;" +
              " latest is ${result.lastOrNull()}"
        }
        return result
      } catch (e: PersistenceException) {
        transacter.get().transaction(shard) { s ->
          s.useConnection { c ->
            val statement = c.createStatement()
            statement.addBatch("""
            |CREATE TABLE schema_version (
            |  version varchar(50) PRIMARY KEY,
            |  installed_by varchar(30) DEFAULT NULL
            |);
            |""".trimMargin())
            statement.executeBatch()
          }
          sortedSetOf<NamedspacedMigration>()
        }
      }
    }
  }

  /**
   * Returns the versions of applied migrations. Throws a [javax.persistence.PersistenceException]
   * if the migrations table has not been initialized.
   */
  fun appliedMigrations(shard: Shard): SortedSet<NamedspacedMigration> {
    return transacter.get().failSafeRead(shard) { session ->
      @Suppress("UNCHECKED_CAST") // createNativeQuery returns a raw Query.
      val query = session.hibernateSession.createNativeQuery(
        "SELECT version FROM schema_version") as Query<String>

      session.disableChecks(listOf(Check.TABLE_SCAN)) {
        query.list().map { NamedspacedMigration.fromNamespacedVersion(it) }.toSortedSet()
      }
    }
  }

  /** Applies all available migrations that haven't yet been applied. */
  fun applyAll(author: String, appliedMigrations: SortedSet<NamedspacedMigration>): MigrationState {
    require(author.matches(Regex("\\w+"))) // Prevent SQL injection.

    val result = mutableMapOf<Shard, ShardMigrationState>()
    for (shard in transacter.get().shards()) {
      val availableMigrations = availableMigrations(shard.keyspace)
      val shardMigrationState = ShardMigrationState(availableMigrations, appliedMigrations)
      for (migration in shardMigrationState.missingMigrations()) {
        val migrationSql = resourceLoader.utf8(migration.path)
        val stopwatch = Stopwatch.createStarted()

        transacter.get().transaction(shard) { s ->
          s.useConnection { c ->
            val migrationStatement = c.createStatement()
            migrationStatement.addBatch(migrationSql)
            migrationStatement.executeBatch()

            val schemaVersion = c.prepareStatement("""
            |INSERT INTO schema_version (version, installed_by) VALUES (?, ?);
            |""".trimMargin())
            schemaVersion.setString(1, migration.toNamespacedVersion())
            schemaVersion.setString(2, author)
            schemaVersion.executeUpdate()
          }
        }

        logger.info { "${qualifier.simpleName} applied $migration in $stopwatch" }
      }

      // All available migrations are applied, so use availableMigrations for both properties.
      result[shard] = ShardMigrationState(
          availableMigrations, TreeSet(appliedMigrations + availableMigrations))
    }
    return MigrationState(result)
  }

  /** Throws an exception unless all available migrations have been applied. */
  fun requireAll(): MigrationState {
    try {
      val result = mutableMapOf<Shard, ShardMigrationState>()
      for (it in transacter.get().shards()) {
        result[it] = requireAll(it)
      }
      return MigrationState(result)
    } catch (e: PersistenceException) {
      throw IllegalStateException("${qualifier.simpleName} is not ready", e)
    }
  }

  @VisibleForTesting
  internal fun requireAll(shard: Shard): ShardMigrationState {
    val availableMigrations = availableMigrations(shard.keyspace)
    val appliedMigrations = appliedMigrations(shard)
    val state = ShardMigrationState(availableMigrations, appliedMigrations)
    val missingMigrations = state.missingMigrations()

    check(missingMigrations.isEmpty()) {
      val shardMessage = if (shard != Shard.SINGLE_SHARD) {
        " shard $shard"
      } else {
        ""
      }
      return@check """
          |${qualifier.simpleName}$shardMessage is missing migrations:
          |  ${missingMigrations.joinToString(separator = "\n  ") { it.path }}
          """.trimMargin()
    }

    return state
  }
}

/** Snapshot of all shards in a cluster. */
internal data class MigrationState(
  val shards: Map<Shard, ShardMigrationState>
)

/** Snapshot of the migration state of a single shard. */
internal data class ShardMigrationState(
  val available: SortedSet<NamedspacedMigration>,
  val applied: SortedSet<NamedspacedMigration>
) {
  fun missingMigrations() = available - applied

  override fun toString(): String {
    return if (available == applied) {
      "(all " + available.size + " migrations applied)"
    } else {
      "(not applied=${available - applied}, not tracked=${applied - available})"
    }
  }
}
