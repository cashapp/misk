package misk.jooq

import jakarta.inject.Inject
import jakarta.inject.Qualifier
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule
import misk.jdbc.SHARED_TEST_DATABASE_POOL
import misk.jooq.config.ConfigurationFactory
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING

/**
 * Verifies that jOOQ's render mapping uses the pool-rewritten database name (from [DataSourceService.config]) rather
 * than the original [DataSourceConfig.database].
 *
 * When a [misk.jdbc.DatabasePool] renames the database (e.g. `my_db` → `my_db__20260827210452__1`), jOOQ's [MappedSchema]
 * output must reflect the renamed database so that generated SQL references the correct schema. Otherwise jOOQ queries
 * hit the original (stale) database while [misk.jdbc.JdbcTestFixture] truncates the pool-allocated one, causing state
 * to leak between tests.
 */
@MiskTest(startService = true)
class JooqRenderMappingWithDatabasePoolTest {

  @MiskTestModule @Suppress("unused") private val module = JooqPooledDatabaseTestModule()

  @Inject @JooqPooledDBIdentifier private lateinit var configurationFactory: ConfigurationFactory

  @Inject @JooqPooledDBIdentifier private lateinit var dataSourceService: DataSourceService

  @Inject @JooqPooledDBIdentifier private lateinit var transacter: JooqTransacter

  @Test
  fun `render mapping output must match the pool-rewritten database name`() {
    val actualDatabase = dataSourceService.config().database!!
    assertThat(actualDatabase)
      .describedAs("The database pool should have rewritten the database name")
      .isNotEqualTo("misk_jooq_pool_test")
      .containsPattern("misk_jooq_pool_test__[0-9]{14}__[0-9]+")

    val configuration = configurationFactory.getConfiguration(JooqTransacter.TransacterOptions())

    val renderMappingOutput = configuration.settings().renderMapping.schemata.single().output

    assertThat(renderMappingOutput)
      .describedAs(
        "jOOQ render mapping output should be the pool-rewritten database name " +
          "('%s'), not the original config database name".format(actualDatabase)
      )
      .isEqualTo(actualDatabase)
  }

  @Test
  fun `jooq queries must target the pool-rewritten database`() {
    val actualDatabase = dataSourceService.config().database!!

    transacter.transaction { (ctx) ->
      ctx
        .newRecord(MOVIE)
        .apply {
          genre = "COMEDY"
          name = "Pool Test Movie"
        }
        .also { it.store() }
    }

    val count = transacter.transaction { (ctx) -> ctx.selectCount().from(MOVIE).fetchOne()!!.component1() }
    assertThat(count).isEqualTo(1)

    // Verify the SQL jOOQ generates references the correct database.
    // Use renderInlined to see the fully qualified table name in the query.
    val configuration = configurationFactory.getConfiguration(JooqTransacter.TransacterOptions())
    val renderedSql = org.jooq.impl.DSL.using(configuration).selectCount().from(MOVIE).getSQL()

    assertThat(renderedSql)
      .describedAs("Rendered SQL should reference the pool-rewritten database '%s'".format(actualDatabase))
      .contains("`$actualDatabase`")
  }
}

class JooqPooledDatabaseTestModule : KAbstractModule() {
  override fun configure() {
    install(DeploymentModule(TESTING))
    install(MiskTestingServiceModule())

    val datasourceConfig =
      DataSourceClusterConfig(
        writer =
          DataSourceConfig(
            type = DataSourceType.MYSQL,
            username = "root",
            password = "",
            database = "misk_jooq_pool_test",
            migrations_resource = "classpath:/db-migrations",
            show_sql = "true",
          ),
        reader = null,
      )
    install(
      JooqModule(
        qualifier = JooqPooledDBIdentifier::class,
        dataSourceClusterConfig = datasourceConfig,
        jooqCodeGenSchemaName = "jooq",
        databasePool = SHARED_TEST_DATABASE_POOL,
        jooqTimestampRecordListenerOptions =
          JooqTimestampRecordListenerOptions(
            install = true,
            createdAtColumnName = "created_at",
            updatedAtColumnName = "updated_at",
          ),
      )
    )
    install(JdbcTestingModule(JooqPooledDBIdentifier::class))
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqPooledDBIdentifier
