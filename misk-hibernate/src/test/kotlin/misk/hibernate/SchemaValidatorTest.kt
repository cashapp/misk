package misk.hibernate

import com.google.inject.Injector
import misk.MiskTestingServiceModule
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.environment.Env
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceConnector
import misk.jdbc.DataSourceService
import misk.jdbc.JdbcModule
import misk.jdbc.SchemaMigratorService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import okio.ByteString
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wisp.config.Config
import wisp.deployment.TESTING
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Table
import javax.sql.DataSource
import kotlin.test.assertTrue

@MiskTest(startService = true)
internal class SchemaValidatorTest {
  @MiskTestModule
  val module = TestModule()
  val env = Env(TESTING.name)
  val config = MiskConfig.load<RootConfig>("schemavalidation", env)

  @Inject @ValidationDb lateinit var transacter: Transacter
  @Inject @ValidationDb lateinit var sessionFactoryService: Provider<SessionFactoryService>

  inner class TestModule : KAbstractModule() {
    override fun configure() {
      install(DeploymentModule(TESTING, env))
      install(MiskTestingServiceModule())

      val qualifier = ValidationDb::class

      val injectorServiceProvider = getProvider(HibernateInjectorAccess::class.java)
      val entitiesProvider = getProvider(setOfType(HibernateEntity::class).toKey(qualifier))

      val sessionFactoryServiceProvider = getProvider(keyOf<SessionFactoryService>(qualifier))
      bind<SessionFactory>()
        .annotatedWith<ValidationDb>()
        .toProvider(keyOf<SessionFactoryService>(qualifier))

      bind(keyOf<Transacter>(qualifier)).toProvider(object : Provider<Transacter> {
        @Inject lateinit var executorServiceFactory: ExecutorServiceFactory
        @Inject lateinit var injector: Injector
        override fun get(): RealTransacter = RealTransacter(
          qualifier = qualifier,
          sessionFactoryService = sessionFactoryServiceProvider.get(),
          readerSessionFactoryService = null,
          config = config.data_source,
          executorServiceFactory = executorServiceFactory,
          hibernateEntities = injector.findBindingsByType(HibernateEntity::class.typeLiteral())
            .map {
              it.provider.get()
            }.toSet()
        )
      }).asSingleton()

      install(JdbcModule(qualifier, config.data_source))

      val connectorProvider = getProvider(keyOf<DataSourceConnector>(qualifier))

      install(object : HibernateEntityModule(qualifier) {
        override fun configureHibernate() {
          addEntities(DbBasicTestTable::class)
          addEntities(DbHibernateOnlyTable::class)
          addEntities(DbMissingColumnsTable::class)
          addEntities(DbNullableMismatchTable::class)
          addEntities(DbBadIdentifierTable::class)
        }
      })

      val dataSourceProvider = getProvider(keyOf<DataSource>(qualifier))
      bind(keyOf<TransacterService>(qualifier)).to(keyOf<SessionFactoryService>(qualifier))
      bind(keyOf<SessionFactoryService>(qualifier)).toProvider(
        Provider {
          SessionFactoryService(
            qualifier = qualifier,
            connector = connectorProvider.get(),
            dataSource = dataSourceProvider,
            hibernateInjectorAccess = injectorServiceProvider.get(),
            entityClasses = entitiesProvider.get()
          )
        }
      ).asSingleton()
      install(
        ServiceModule<TransacterService>(qualifier)
          .enhancedBy<SchemaMigratorService>(qualifier)
          .dependsOn<DataSourceService>(qualifier)
      )
    }
  }

  // TODO (maacosta) Breakup into smaller unit tests.
  private val schemaValidationErrorMessage: String by lazy {
    assertThrows<IllegalStateException> {
      SchemaValidator().validate(transacter, sessionFactoryService.get().hibernateMetadata)
    }.message!!
  }

  @Test
  fun findMissingTablesInHibernate() {
    assertThat(schemaValidationErrorMessage).contains(
      "Hibernate missing tables [database_only_table, unquoted_database_table]"
    )
  }

  @Test
  fun findMissingTablesInDb() {
    assertThat(schemaValidationErrorMessage).contains(
      "Database missing tables [hibernate_only_table]"
    )
  }

  @Test
  fun findMissingColumnsInHibernate() {
    assertThat(schemaValidationErrorMessage).contains(
      "Hibernate entity \"missing_columns_table\" is missing columns " +
        "[tbl4_string_column_database] " +
        "expected in table \"missing_columns_table\""
    )
  }

  @Test
  fun findMissingColumnsInDb() {
    assertThat(schemaValidationErrorMessage).contains(
      "Database table \"missing_columns_table\" is missing columns " +
        "[tbl4_int_column_hibernate, tbl4_string_column_hibernate] " +
        "found in hibernate \"missing_columns_table\""
    )
  }

  @Test
  fun findNullableColumnsInHibernate() {
    assertThat(schemaValidationErrorMessage).contains(
      "ERROR at schemavalidation.nullable_mismatch_table.tbl5_hibernate_null:\n" +
        "  Column nullable_mismatch_table.tbl5_hibernate_null is NOT NULL in database " +
        "but tbl5_hibernate_null is nullable in hibernate"
    )
  }

  @Test
  fun itOkWithNotNullableColumnWithDefaults() {
    assertThat(schemaValidationErrorMessage).doesNotContain(
      "ERROR at schemavalidation.nullable_mismatch_table.tbl5_hibernate_null_default:"
    )
  }

  @Test
  fun catchBadTables() {
    assertThat(schemaValidationErrorMessage).contains(
      "\"BAD_identifier_table\" should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "\"BAD_identifier_table\" should exactly match hibernate \"bad_identifier_table\""
    )
  }

  @Test
  fun quotaTableIsOk() {
    assertThat(schemaValidationErrorMessage).doesNotContain("quoted_basic_table")
  }

  @Test
  fun toSnakeCase() {
    assertThat("tbl6DatabaseCamelcase".toSnakeCase()).isEqualTo("tbl6_database_camelcase")
    assertThat("MarioAcosta".toSnakeCase()).isEqualTo("mario_acosta")
    assertThat("Coca-Cola".toSnakeCase()).isEqualTo("coca_cola")
  }

  @Test
  fun catchBadColumnNames() {
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6CamelCase should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6_UPPER_UNDERSCORE should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6_MixEd_UNDERScore should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6-lower-hyphen should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6DatabaseCamelcase should be in lower_snake_case"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6DatabaseCamelcase should exactly match hibernate tbl6_database_camelcase"
    )
    assertThat(schemaValidationErrorMessage).contains(
      "tbl6_hibernate_camelcase should exactly match hibernate tbl6HibernateCamelcase"
    )
  }

  @Test
  fun catchNotReallyUniqueColumnNames() {
    val duplicateIds =
      schemaValidationErrorMessage.contains(
        "Duplicate identifiers: [[tbl6NotReallyUnique, tbl6_not_really_unique]]"
      ) ||
        schemaValidationErrorMessage.contains(
          "Duplicate identifiers: [[tbl6_not_really_unique, tbl6NotReallyUnique]]"
        )

    assertTrue(
      duplicateIds,
      "Expected duplicate ids for: [[tbl6_not_really_unique, tbl6NotReallyUnique]]"
    )

    assertThat(schemaValidationErrorMessage).contains(
      "Duplicate identifiers: " +
        "[[tbl6NotReallyUnique, tbl6_not_REALLY_unique], " +
        "[tbl6NotReallyUnique2, tbl6_not_really_unique2]]"
    )
  }

  data class RootConfig(val data_source: DataSourceConfig) : Config

  @Qualifier
  @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
  annotation class ValidationDb

  @Entity
  @Table(name = "`quoted_basic_table`")
  class DbBasicTestTable : DbUnsharded<DbBasicTestTable>, DbTimestampedEntity {

    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbBasicTestTable>

    @Column
    var tbl1_string_nullable: String? = null

    @Column
    var tbl1_int_nullable: Int? = null

    @Column
    var tbl1_bin_nullable: ByteString? = null

    @Column(nullable = false)
    lateinit var tbl1_string: String

    @Column(nullable = false)
    var tbl1_int: Int = 0

    @Column(name = "`tbl1_bin`", nullable = false)
    lateinit var anotherNameForThisBinColumn: ByteString

    @Column
    override lateinit var updated_at: Instant

    @Column
    override lateinit var created_at: Instant
  }

  @Entity
  @Table(name = "`hibernate_only_table`")
  class DbHibernateOnlyTable : DbUnsharded<DbHibernateOnlyTable> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbHibernateOnlyTable>

    @Column(nullable = false)
    lateinit var string_column: String

    @Column(nullable = false)
    var int_column: Int = 0
  }

  @Entity
  @Table(name = "`missing_columns_table`")
  class DbMissingColumnsTable : DbUnsharded<DbMissingColumnsTable> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbMissingColumnsTable>

    @Column(nullable = false)
    lateinit var tbl4_string_column: String

    @Column(nullable = false)
    var tbl4_int_column: Int = 0

    @Column(nullable = false)
    lateinit var tbl4_string_column_hibernate: String

    @Column(nullable = false)
    var tbl4_int_column_hibernate: Int = 0
  }

  @Entity
  @Table(name = "`nullable_mismatch_table`")
  class DbNullableMismatchTable : DbUnsharded<DbNullableMismatchTable> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbNullableMismatchTable>

    @Column(nullable = false)
    var tbl5_both_notnull: Int = 0

    @Column
    var tbl5_hibernate_null: Int = 0

    @Column
    var tbl5_hibernate_null_default: Int = 0

    @Column
    var tbl5_both_null: Int = 0

    @Column(nullable = false)
    var tbl5_database_null: Int = 0
  }

  @Entity
  @Table(name = "bad_identifier_table")
  class DbBadIdentifierTable : DbUnsharded<DbBadIdentifierTable> {
    @javax.persistence.Id
    @GeneratedValue
    override lateinit var id: Id<DbBadIdentifierTable>

    @Column
    var tbl6CamelCase: Int = 0

    @Column
    var tbl6_UPPER_UNDERSCORE: Int = 0

    @Column
    var tbl6_MixEd_UNDERScore: Int = 0

    @Column(name = "`tbl6-lower-hyphen`")
    var tbl6LowerHyphen: Int = 0

    @Column
    var tbl6NotReallyUnique: Int = 0

    @Column
    var tbl6_not_REALLY_unique: Int = 0

    @Column
    var tbl6NotReallyUnique2: Int = 0

    @Column
    var tbl6_not_really_unique2: Int = 0

    @Column
    var tbl6HibernateCamelcase: Int = 0

    @Column
    var tbl6_database_camelcase: Int = 0
  }
}
