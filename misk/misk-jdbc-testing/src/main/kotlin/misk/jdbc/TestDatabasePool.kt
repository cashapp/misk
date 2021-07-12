package misk.jdbc

import wisp.logging.getLogger
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import java.util.regex.Pattern

/**
 * A [DatabasePool] that is used in tests to get a unique database for each test suite.
 *
 * See [misk.hibernate.HibernateTestingModule] for usage instructions. */
val SHARED_TEST_DATABASE_POOL = TestDatabasePool(
  MySqlTestDatabasePoolBackend(
    DataSourceConfig(type = DataSourceType.MYSQL, username = "root").withDefaults()
  ),
  Clock.systemUTC()
)

/**
 * Manages an inventory of databases for testing. Databases are named like
 * `movies__20190730__5` where `movies` is the database name in a [DataSourceConfig], `20190730` is
 * today's date, and 5 is a sequence number.
 *
 * These are used _only_ in tests, so that each test gets a reserved database to avoid
 * parallelism issues.
 *
 * Thread-safe.
 */
class TestDatabasePool(
  val backend: Backend,
  val clock: Clock
) : DatabasePool {
  /** The key is the config's database name. */
  private val poolsByKey = Collections.synchronizedMap(mutableMapOf<String, ConfigSpecificPool>())

  override fun takeDatabase(config: DataSourceConfig): DataSourceConfig {
    // We don't yet have a mechanism to create Vitess databases from Misk.
    // TODO: Supporting pooled Vitess DBs would be pretty rad.
    if (config.type != DataSourceType.MYSQL) return config

    val pooled = getPool(config)
    return config.copy(database = pooled.takeDatabase())
  }

  override fun releaseDatabase(config: DataSourceConfig) {
    getPool(config).releaseDatabase(config.database!!)
  }

  /**
   * Drops all databases that were created by an allocator which are older than the retention
   * duration of this allocator.
   *
   * @param retention Must be longer than any test could possibly run for.
   */
  fun pruneOldDatabases(retention: Duration = Duration.ofDays(2)) {
    for (pool in poolsByKey.values) {
      pool.pruneOldDatabases(retention)
    }
  }

  /** Return a config-specific pool, creating it if necessary. */
  internal fun getPool(config: DataSourceConfig): ConfigSpecificPool {
    var key = config.database!!
    for (pool in poolsByKey.values) {
      if (key.startsWith(pool.key)) {
        key = pool.key
        break
      }
    }
    return poolsByKey.computeIfAbsent(key) { ConfigSpecificPool(key, config.type) }
  }

  /** A pool of databases for a particular config. Thread-safe. */
  internal inner class ConfigSpecificPool(
    val key: String,
    val type: DataSourceType
  ) {
    private val databaseNameRegex = Regex("""(${Pattern.quote(key)})__([0-9]{8})__([0-9]{1,5})""")

    private val formatter = DateTimeFormatter.BASIC_ISO_DATE

    /** Database names that are available. */
    internal val pool = LinkedBlockingDeque<String>()

    /** Decodes a database name string. */
    fun decode(databaseName: String): DatabaseName? {
      val matchResult = databaseNameRegex.matchEntire(databaseName) ?: return null
      return DatabaseName(
        matchResult.groups[1]!!.value,
        matchResult.groups[2]!!.value.toLong(),
        matchResult.groups[3]!!.value.toInt()
      )
    }

    /** Returns all databases for this key. */
    fun getDatabases(): List<DatabaseName> {
      return backend.showDatabases().mapNotNull { decode(it) }
    }

    fun takeDatabase(): String {
      val pooled = pool.poll()
      if (pooled != null) return pooled

      return allocateDatabase()
    }

    fun releaseDatabase(databaseName: String) {
      pool.add(databaseName)
    }

    /** Creates a database and returns its name. */
    fun allocateDatabase(): String {
      val today = LocalDate.now(clock)
      val todayYearMonthDay = today.format(formatter).toLong()

      val todaysLatest = getDatabases()
        .filter { it.yearMonthDay == todayYearMonthDay }
        .maxByOrNull { it.version }

      val nextVersion = (todaysLatest?.version ?: 0) + 1

      var databaseName =
        DatabaseName(key, todayYearMonthDay, nextVersion)

      // Keep trying to create a database until we have found an unused name.
      while (true) {
        try {
          backend.createDatabase(databaseName.toString())
          break
        } catch (_: SQLException) {
          logger.info { "Lost a race trying to allocate a test database $databaseName" }
          databaseName = databaseName.copy(version = databaseName.version + 1)
        }
      }

      return databaseName.toString()
    }

    fun pruneOldDatabases(retention: Duration = Duration.ofDays(2)) {
      val evictBefore = LocalDate.now(clock).minus(Period.ofDays(retention.toDays().toInt()))
      val evictBeforeYearMonthDay = evictBefore.format(formatter).toLong()

      val oldDatabases = if (retention.isZero) {
        getDatabases()
      } else {
        getDatabases().filter { it.yearMonthDay < evictBeforeYearMonthDay }
      }

      for (database in oldDatabases) {
        backend.dropDatabase("$database")
      }
    }
  }

  /** A backend for a [ConfigSpecificPool]. */
  interface Backend {
    /** Returns a list of *all* databases present in the data source. */
    fun showDatabases(): Set<String>

    /**
     * Drops the indicated database from the data source.
     *
     * Throws [PersistenceException] if the database cannot be dropped (i.e. it does not exist).
     */
    fun dropDatabase(name: String)

    /**
     * Attempts to create the indicated database in the data source.
     *
     * Throws [PersistenceException] if the database already exists.
     */
    fun createDatabase(name: String)
  }

  companion object {
    private val logger = getLogger<ConfigSpecificPool>()
  }

  data class DatabaseName(val name: String, val yearMonthDay: Long, val version: Int) {
    override fun toString() = "${name}__${yearMonthDay}__$version"
  }
}
