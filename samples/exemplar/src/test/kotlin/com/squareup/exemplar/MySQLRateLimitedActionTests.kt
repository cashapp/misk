package com.squareup.exemplar

import com.google.inject.Module
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.JdbcTestingModule
import misk.ratelimiting.bucket4j.mysql.MySQLBucket4jRateLimiterModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import wisp.deployment.TESTING

@MiskTest(startService = true)
class MySQLRateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(ExemplarTestModule())
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>("exemplar-testing", TESTING)
      install(JdbcTestingModule(RateLimits::class))
      install(
        HibernateModule(
          RateLimits::class,
          RateLimitsReadOnly::class,
          DataSourceClusterConfig(
            writer = config.mysql_data_source,
            reader = config.mysql_data_source
          )
        )
      )
      install(
        MySQLBucket4jRateLimiterModule(RateLimits::class, TABLE_NAME, ID_COLUMN, STATE_COLUMN)
      )
      bind<MeterRegistry>().toInstance(SimpleMeterRegistry())
    }
  }

  @Inject @RateLimits
  private lateinit var dataSourceService: DataSourceService

  override fun setException() {
    dataSourceService.stopAsync().awaitTerminated()
  }

  private data class RootConfig(
    val mysql_data_source: DataSourceConfig
  ) : Config

  companion object {
    private const val TABLE_NAME = "rate_limit_buckets"
    private const val ID_COLUMN = "id"
    private const val STATE_COLUMN = "state"
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class RateLimits

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION)
internal annotation class RateLimitsReadOnly
