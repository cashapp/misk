package misk.jooq.config

import jakarta.inject.Inject
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jooq.IsolationLevelAwareConnectionProvider
import misk.jooq.JooqTransacter
import misk.jooq.TransactionIsolationLevel
import misk.jooq.config.ClientJooqTestingModule.Companion.JOOQ_CONFIG_EXTENSION
import misk.jooq.listeners.AvoidUsingSelectStarListener
import misk.jooq.listeners.JooqSQLLogger
import misk.jooq.listeners.JooqTimestampRecordListener
import misk.jooq.listeners.JooqTimestampRecordListenerOptions
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.jooq.Configuration
import org.jooq.SQLDialect
import org.jooq.conf.MappedSchema
import org.jooq.impl.DSL
import org.jooq.impl.DataSourceConnectionProvider
import org.jooq.impl.DefaultExecuteListenerProvider
import org.jooq.impl.DefaultTransactionProvider
import org.jooq.kotlin.renderMapping
import org.jooq.kotlin.schemata
import org.jooq.kotlin.settings
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MiskTest(startService = true)
class CachedConfigurationFactoryTest {
    @Suppress("unused")
    @MiskTestModule
    private val module = ClientJooqTestingModule()

    @Inject
    private lateinit var clock: Clock

    @Inject
    @JooqDBReadOnlyIdentifier
    private lateinit var readerConfigurationFactory: ConfigurationFactory

    @Inject
    @JooqDBReadOnlyIdentifier
    private lateinit var readerDataSourceService: DataSourceService

    @Inject
    @JooqDBIdentifier
    private lateinit var writerConfigurationFactory: ConfigurationFactory

    @Inject
    @JooqDBIdentifier
    private lateinit var writerDataSourceService: DataSourceService

    @Inject
    @JooqDBIdentifier
    private lateinit var transacter: JooqTransacter

    @Test
    fun `multithreaded transaction initiation should result in one Configuration per transaction isolation level`() {
        val threadCount = TransactionIsolationLevel.entries.size * 4
        val startLatch = CountDownLatch(threadCount)
        val threads = (0..<threadCount).map { idx ->
            Thread {
                val isolationLevel = TransactionIsolationLevel.entries[idx % 4]
                startLatch.countDown()
                require(startLatch.await(5, TimeUnit.SECONDS)) { "Timed out waiting for latch" }
                val time =
                    transacter.transaction(JooqTransacter.TransacterOptions(isolationLevel = isolationLevel)) { session ->
                        session.ctx.select(DSL.now()).fetchOne { it.component1().toInstant() }
                    }
                println("Got $time")
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val cacheContents = (writerConfigurationFactory as CachedConfigurationFactory).cacheContents
        assertThat(cacheContents).hasSize(
            TransactionIsolationLevel.entries.size
        )
        assertThat(cacheContents.map { it.key }).containsExactlyInAnyOrder(
            TransactionIsolationLevel.READ_UNCOMMITTED,
            TransactionIsolationLevel.READ_COMMITTED,
            TransactionIsolationLevel.REPEATABLE_READ,
            TransactionIsolationLevel.SERIALIZABLE,
        )
    }

    @Test
    fun `configuration factory should yield an identical configuration to old method`() {
        val options = JooqTransacter.TransacterOptions()
        val writerConfig = writerConfigurationFactory.getConfiguration(options)
        val readerConfig = readerConfigurationFactory.getConfiguration(options)
        // Method arguments taken from jOOQ module setup in ClientJooqTestingModule
        val legacyWriterConfig = buildOldConfiguration(
            clock,
            writerDataSourceService.config(),
            writerDataSourceService,
            "jooq",
            JooqTimestampRecordListenerOptions(
                install = true,
                createdAtColumnName = "created_at",
                updatedAtColumnName = "updated_at",
            ),
            options,
            JOOQ_CONFIG_EXTENSION
        )
        val legacyReaderConfig = buildOldConfiguration(
            clock,
            readerDataSourceService.config(),
            readerDataSourceService,
            "jooq",
            JooqTimestampRecordListenerOptions(
                install = true,
                createdAtColumnName = "created_at",
                updatedAtColumnName = "updated_at",
            ),
            options,
            JOOQ_CONFIG_EXTENSION
        )
        assertThat(writerConfig).usingRecursiveComparison().isEqualTo(legacyWriterConfig)
        assertThat(readerConfig).usingRecursiveComparison().isEqualTo(legacyReaderConfig)
    }

    private fun buildOldConfiguration(
        clock: Clock,
        dataSourceConfig: DataSourceConfig,
        dataSourceService: DataSourceService,
        jooqCodeGenSchemaName: String,
        jooqTimestampRecordListenerOptions: JooqTimestampRecordListenerOptions,
        options: JooqTransacter.TransacterOptions,
        jooqConfigExtension: Configuration.() -> Unit
    ): Configuration {
        val settings = settings {
            isExecuteWithOptimisticLocking = true
            renderMapping {
                schemata {
                    add(
                        MappedSchema()
                            .withInput(jooqCodeGenSchemaName)
                            .withOutput(dataSourceConfig.database)
                    )
                }
            }
        }

        val connectionProvider = IsolationLevelAwareConnectionProvider(
            dataSourceConnectionProvider = DataSourceConnectionProvider(dataSourceService.dataSource),
            transacterOptions = options,
        )

        return DSL.using(connectionProvider, SQLDialect.MYSQL, settings)
            .apply {
                configuration().set(
                    DefaultTransactionProvider(
                        configuration().connectionProvider(),
                        false,
                    ),
                ).apply {
                    val executeListeners = buildList {
                        add(DefaultExecuteListenerProvider(AvoidUsingSelectStarListener()))
                        if (dataSourceConfig.show_sql.toBoolean()) {
                            add(DefaultExecuteListenerProvider(JooqSQLLogger()))
                        }
                    }
                    set(*executeListeners.toTypedArray())

                    if (jooqTimestampRecordListenerOptions.install) {
                        set(
                            JooqTimestampRecordListener(
                                clock = clock,
                                createdAtColumnName = jooqTimestampRecordListenerOptions.createdAtColumnName,
                                updatedAtColumnName = jooqTimestampRecordListenerOptions.updatedAtColumnName,
                            ),
                        )
                    }
                }.apply(jooqConfigExtension)
            }.configuration()
    }
}