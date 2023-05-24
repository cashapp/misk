package misk.jdbc

import com.google.inject.Module
import com.google.inject.Singleton
import com.google.inject.util.Modules
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@MiskTest(startService = false)
class TestDatabasePoolTest {
  @Suppress("unused")
  @MiskTestModule val module: Module = Modules.combine(
    MiskTestingServiceModule(),
    object : KAbstractModule() {
      override fun configure() {
        bind<TestDatabasePool.Backend>().to<FakeDatabaseBackend>()
      }
    }
  )

  @Inject private lateinit var clock: Clock
  @Inject private lateinit var backend: FakeDatabaseBackend

  private val config = DataSourceConfig(
    type = DataSourceType.MYSQL,
    database = "test",
    username = "root",
    password = ""
  )

  private lateinit var testDatabasePool: TestDatabasePool

  @BeforeEach fun setUp() {
    testDatabasePool = TestDatabasePool(backend, clock)
  }

  @Test fun listsExistingDatabases() {
    backendHasDatabases()

    assertThat(getAllDatabasesFromPool()).containsExactly(
      TestDatabasePool.DatabaseName("test", 20171227L, 1),
      TestDatabasePool.DatabaseName("test", 20171228L, 1),
      TestDatabasePool.DatabaseName("test", 20171229L, 1),
      TestDatabasePool.DatabaseName("test", 20171230L, 1),
      TestDatabasePool.DatabaseName("test", 20171231L, 1),
      TestDatabasePool.DatabaseName("test", 20180101L, 1),
      TestDatabasePool.DatabaseName("test", 20180101L, 2),
      TestDatabasePool.DatabaseName("test", 20180101L, 3)
    )
  }

  @Test fun dropsDatabasesOlderThan48Hours() {
    backendHasDatabases()
    testDatabasePool.takeDatabase(config)

    testDatabasePool.pruneOldDatabases()

    assertThat(getAllDatabasesFromPool()).containsExactly(
      TestDatabasePool.DatabaseName("test", 20171230L, 1), // Existing
      TestDatabasePool.DatabaseName("test", 20171231L, 1), // Existing
      TestDatabasePool.DatabaseName("test", 20180101L, 1), // Existing
      TestDatabasePool.DatabaseName("test", 20180101L, 2), // Existing
      TestDatabasePool.DatabaseName("test", 20180101L, 3), // Existing
      TestDatabasePool.DatabaseName("test", 20180101L, 4) // New
    )
  }

  @Test fun poolRequiresRegistrationBeforeItCanBeDropped() {
    // Add databases to the backend.
    backend.databases.add("movies__20171225__1")
    backend.databases.add("directors__20171225__1")
    backend.databases.add("actors__20171225__1")

    // Do not register these with the database pool. The pool should be empty.
    assertThat(getAllDatabasesFromPool()).isEmpty()

    // This should be a no-op.
    testDatabasePool.pruneOldDatabases()

    assertThat(backend.databases).containsExactlyInAnyOrder(
      "movies__20171225__1",
      "directors__20171225__1",
      "actors__20171225__1"
    )
  }

  @Test fun allocatesNewDatabasesWithIncrementalNames() {
    assertThat(testDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__1")
    assertThat(backend.databases).containsExactly("test__20180101__1")

    assertThat(getAllDatabasesFromPool()).contains(
      TestDatabasePool.DatabaseName(
        name = "test",
        yearMonthDay = 20180101L,
        version = 1
      )
    )

    assertThat(testDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__2")
    assertThat(backend.databases).containsExactly(
      "test__20180101__1",
      "test__20180101__2"
    )
  }

  @Test fun releasesDatabaseNamesForReuse() {
    assertThat(testDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__1")
    assertThat(testDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__2")
    testDatabasePool.releaseDatabase(config.copy(database = "test__20180101__1"))
    assertThat(testDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__1")
  }

  @Test fun integrationTest() {
    val realDatabasePool = TestDatabasePool(MySqlTestDatabasePoolBackend(config), clock)
    realDatabasePool.getPool(config)
    realDatabasePool.pruneOldDatabases(retention = Duration.ZERO)

    // Create a database. This will be the first one that the pool tries to create.
    realDatabasePool.backend.createDatabase("test__20180101__1")

    // Demonstrate that attempting to create an existing database throws.
    assertThrows<SQLException> {
      realDatabasePool.backend.createDatabase("test__20180101__1")
    }

    // Assert that the database pool does not yet know about any database names.
    assertThat(realDatabasePool.getPool(config).pool).isEmpty()

    // At this point, the pool has tried to allocate "test__20180101__1", failed, and bumped the
    // sequence number.
    assertThat(realDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__2")

    // If we return a name to the database pool, it will be the next database to be taken.
    realDatabasePool.releaseDatabase(config.copy(database = "test__20180101__1"))
    assertThat(realDatabasePool.takeDatabase(config).database).isEqualTo("test__20180101__1")
  }

  private fun backendHasDatabases() {
    val todaysDatabases = listOf(
      "test__20180101__1",
      "test__20180101__2",
      "test__20180101__3"
    )
    val oldDatabases = listOf(
      "test__20171231__1",
      "test__20171230__1",
      "test__20171229__1",
      "test__20171228__1",
      "test__20171227__1"
    )
    backend.databases.addAll(todaysDatabases)
    backend.databases.addAll(oldDatabases)
  }

  private fun getAllDatabasesFromPool(): List<TestDatabasePool.DatabaseName> {
    return testDatabasePool.getPool(config).getDatabases()
  }
}

@Singleton
private class FakeDatabaseBackend @Inject constructor() : TestDatabasePool.Backend {
  val databases = mutableSetOf<String>()

  override fun showDatabases(): Set<String> = databases.sorted().toSet()

  override fun dropDatabase(name: String) {
    databases.remove(name)
  }

  override fun createDatabase(name: String) {
    if (name in databases) throw SQLException()
    databases.add(name)
  }
}
