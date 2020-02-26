package com.squareup.misk.sqldelight.test

import com.cashapp.misk.sqldelight.MiskDriver
import com.google.inject.util.Modules
import io.opentracing.Tracer
import misk.MiskTestingServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.toKey
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.logging.LogCollectorModule
import misk.testing.MockTracingBackendModule
import misk.time.FakeClockModule
import org.hibernate.SessionFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Qualifier

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
annotation class SqlDelight

class SqlDelightTestingModule : KAbstractModule() {
  override fun configure() {
    install(LogCollectorModule())
    install(Modules.override(MiskTestingServiceModule()).with(FakeClockModule(),
            MockTracingBackendModule()))
    install(EnvironmentModule(Environment.TESTING))

    install(HibernateTestingModule(SqlDelight::class))
    install(HibernateModule(SqlDelight::class, DataSourceConfig(
        type = DataSourceType.MYSQL,
        database = "sqldelight",
        username = "root",
        password = ""
    )))

    val databaseKey = TestDatabase::class.toKey(SqlDelight::class)
    binder().bind(databaseKey).toProvider(object : Provider<TestDatabase> {
      @Inject @SqlDelight lateinit var sessionFactoryProvider: Provider<SessionFactory>

      override fun get(): TestDatabase {
        val sessionFactory = sessionFactoryProvider.get()
        val driver = MiskDriver(sessionFactory)
        TestDatabase.Schema.create(driver)
        return TestDatabase(driver)
      }
    }).asSingleton()
  }

}