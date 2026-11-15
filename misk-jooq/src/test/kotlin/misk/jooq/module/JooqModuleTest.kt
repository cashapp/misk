package misk.jooq.module

import com.google.inject.util.Modules
import jakarta.inject.Inject
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.jooq.JooqModule
import misk.jooq.JooqTransacter
import misk.jooq.config.ClientJooqTestingModule
import misk.jooq.config.ClientJooqTestingModule.Companion.JOOQ_CONFIG_EXTENSION
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.config.JooqDBReadOnlyIdentifier
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class JooqModuleTest {
  @MiskTestModule
  @Suppress("unused")
  private val module =
    Modules.override(ClientJooqTestingModule())
      .with(
        JdbcModule(
          qualifier = JooqDBIdentifier::class,
          config = dataSourceConfig.writer,
          readerQualifier = JooqDBReadOnlyIdentifier::class,
          readerConfig = dataSourceConfig.reader,
          databasePool = RealDatabasePool,
          installHealthCheck = true,
          installSchemaMigrator = true,
        ),
        JooqModule(
          qualifier = JooqDBIdentifier::class,
          dataSourceClusterConfig = dataSourceConfig,
          jooqCodeGenSchemaName = "jooq",
          jooqTimestampRecordListenerOptions =
            JooqTimestampRecordListenerOptions(
              install = true,
              createdAtColumnName = "created_at",
              updatedAtColumnName = "updated_at",
            ),
          readerQualifier = JooqDBReadOnlyIdentifier::class,
          jooqConfigExtension = JOOQ_CONFIG_EXTENSION,
          jdbcModuleAlreadySetup = true,
        ),
      )

  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter
  @Inject @JooqDBReadOnlyIdentifier private lateinit var readTransacter: JooqTransacter

  @Test
  fun `jooq transacter remains functional`() {
    val record =
      transacter.transaction { session ->
        session.ctx
          .newRecord(MOVIE)
          .apply {
            genre = Genre.HORROR.name
            name = "Carrie"
          }
          .also { it.store() }
      }
    assertThat(record.name).isEqualTo("Carrie")

    val readOnlyRecords = readTransacter.transaction { session -> session.ctx.selectFrom(MOVIE).fetch() }
    assertThat(readOnlyRecords).hasSize(1)
    with(readOnlyRecords.single()) {
      assertThat(genre).isEqualTo("HORROR")
      assertThat(name).isEqualTo("Carrie")
    }
  }

  companion object {
    private val dataSourceConfig =
      DataSourceClusterConfig(
        writer =
          DataSourceConfig(
            type = DataSourceType.MYSQL,
            username = "root",
            password = "",
            database = "misk_jooq_testing_writer",
            migrations_resource = "classpath:/db-migrations",
            show_sql = "true",
          ),
        reader =
          DataSourceConfig(
            type = DataSourceType.MYSQL,
            username = "root",
            password = "",
            // Migrations applied to the writer DB only
            database = "misk_jooq_testing_writer",
            migrations_resource = "classpath:/db-migrations",
            show_sql = "true",
          ),
      )
  }
}
