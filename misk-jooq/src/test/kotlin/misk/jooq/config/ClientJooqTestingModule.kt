package misk.jooq.config

import jakarta.inject.Qualifier
import kotlin.collections.addAll
import misk.MiskTestingServiceModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.JdbcTestingModule
import misk.jooq.JooqModule
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import misk.jooq.listeners.RecordSignatureListener
import misk.jooq.listeners.TableSignatureDetails
import misk.jooq.testgen.tables.references.RECORD_SIGNATURE_TEST
import misk.logging.LogCollectorModule
import org.jooq.Configuration
import org.jooq.RecordListenerProvider
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultRecordListenerProvider
import wisp.deployment.TESTING

class ClientJooqTestingModule : KAbstractModule() {
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
            database = "misk_jooq_testing_writer",
            migrations_resource = "classpath:/db-migrations",
            show_sql = "true",
          ),
        reader =
          DataSourceConfig(
            type = DataSourceType.MYSQL,
            username = "root",
            password = "",
            database = "misk_jooq_testing_reader",
            migrations_resource = "classpath:/db-migrations",
            show_sql = "true",
          ),
      )
    install(
      JooqModule(
        qualifier = JooqDBIdentifier::class,
        dataSourceClusterConfig = datasourceConfig,
        jooqCodeGenSchemaName = "jooq",
        jooqTimestampRecordListenerOptions =
          JooqTimestampRecordListenerOptions(
            install = true,
            createdAtColumnName = "created_at",
            updatedAtColumnName = "updated_at",
          ),
        readerQualifier = JooqDBReadOnlyIdentifier::class,
        jooqConfigExtension = JOOQ_CONFIG_EXTENSION,
      )
    )
    install(JdbcTestingModule(JooqDBIdentifier::class))
    install(LogCollectorModule())
  }

  companion object {
    val JOOQ_CONFIG_EXTENSION: Configuration.() -> Unit = {
      // Since JooqTimestampRecordListener might overwrite record listeners,
      // we need to ensure both timestamp and signature listeners are present
      val recordListeners = mutableListOf<RecordListenerProvider>()

      // Add any existing record listeners (like JooqTimestampRecordListener)
      recordListeners.addAll(this.recordListenerProviders())

      // Add our RecordSignatureListener
      recordListeners.add(
        DefaultRecordListenerProvider(
          RecordSignatureListener(
            recordHasher = FakeRecordHasher(),
            tableSignatureDetails =
              listOf(
                TableSignatureDetails(
                  signatureKeyName = "signature-record-test",
                  table = RECORD_SIGNATURE_TEST,
                  columns =
                    listOf(
                      RECORD_SIGNATURE_TEST.NAME,
                      RECORD_SIGNATURE_TEST.UPDATED_BY,
                      RECORD_SIGNATURE_TEST.BINARY_DATA,
                    ),
                  signatureRecordColumn = RECORD_SIGNATURE_TEST.RECORD_SIGNATURE,
                  allowNullSignatures = false,
                )
              ),
          )
        )
      )

      set(*recordListeners.toTypedArray())

      // Add execute listeners separately
      val existingExecuteListeners = this.executeListenerProviders().toMutableList()
      existingExecuteListeners.add(DefaultExecuteListenerProvider(DeleteOrUpdateWithoutWhereListener()))
      set(*existingExecuteListeners.toTypedArray())
    }
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBIdentifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class JooqDBReadOnlyIdentifier
