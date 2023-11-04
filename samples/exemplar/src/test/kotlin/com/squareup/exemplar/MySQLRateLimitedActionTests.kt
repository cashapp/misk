package com.squareup.exemplar

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.google.inject.Module
import com.google.inject.Provides
import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.prometheus.client.CollectorRegistry
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import misk.aws.dynamodb.testing.DockerDynamoDbModule
import misk.aws.dynamodb.testing.DynamoDbTable
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.DeploymentModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceService
import misk.jdbc.JdbcModule
import misk.jdbc.JdbcTestingModule
import misk.ratelimiting.bucket4j.dynamodb.v1.DynamoDbV1Bucket4jRateLimiterModule
import misk.ratelimiting.bucket4j.mysql.MySQLBucket4jRateLimiterModule
import misk.ratelimiting.bucket4j.redis.RedisBucket4jRateLimiterModule
import misk.redis.testing.DockerRedis
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import wisp.deployment.TESTING

@MiskTest(startService = true)
class MySQLRateLimitedActionTests : AbstractRateLimitedActionTests() {
  @Suppress("unused")
  @MiskTestModule val module: Module = object : KAbstractModule() {
    override fun configure() {
      install(ExemplarTestModule())
      install(DeploymentModule(TESTING))
      val config = MiskConfig.load<RootConfig>("exemplar", TESTING)
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
    }

    @Provides @Singleton
    // In prod this is provided by Skim
    fun provideMeterRegistry(collectorRegistry: CollectorRegistry): MeterRegistry {
      return PrometheusMeterRegistry(
        PrometheusConfig.DEFAULT, collectorRegistry, Clock.SYSTEM
      )
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
