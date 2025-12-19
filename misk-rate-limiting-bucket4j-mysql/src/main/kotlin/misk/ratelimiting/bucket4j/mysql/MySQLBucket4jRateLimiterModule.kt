package misk.ratelimiting.bucket4j.mysql

import com.google.inject.Injector
import com.google.inject.Provides
import io.github.bucket4j.distributed.jdbc.BucketTableSettings
import io.github.bucket4j.distributed.jdbc.PrimaryKeyMapper
import io.github.bucket4j.distributed.jdbc.SQLProxyConfiguration
import io.github.bucket4j.distributed.proxy.ClientSideConfig
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.mysql.MySQLSelectForUpdateBasedProxyManager
import io.micrometer.core.instrument.MeterRegistry
import jakarta.inject.Singleton
import java.time.Clock
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.jdbc.DataSourceService
import wisp.ratelimiting.RateLimitPruner
import wisp.ratelimiting.RateLimiter
import wisp.ratelimiting.bucket4j.Bucket4jRateLimiter
import wisp.ratelimiting.bucket4j.ClockTimeMeter

class MySQLBucket4jRateLimiterModule
@JvmOverloads
constructor(
  private val qualifier: KClass<out Annotation>,
  private val tableName: String,
  private val idColumn: String,
  private val stateColumn: String,
  private val prunerPageSize: Long = 1000L,
) : KAbstractModule() {
  override fun configure() {
    requireBinding<Clock>()
    requireBinding<MeterRegistry>()
    requireBinding(keyOf<DataSourceService>(qualifier))
  }

  @Provides
  @Singleton
  fun providedRateLimiter(clock: Clock, injector: Injector, meterRegistry: MeterRegistry): RateLimiter {
    val dataSourceService = injector.getInstance(keyOf<DataSourceService>(qualifier))
    val sqlConfiguration =
      SQLProxyConfiguration.builder()
        .withPrimaryKeyMapper(PrimaryKeyMapper.STRING)
        .withTableSettings(BucketTableSettings.customSettings(tableName, idColumn, stateColumn))
        .withClientSideConfig(ClientSideConfig.getDefault().withClientClock(ClockTimeMeter(clock)))
        .build(dataSourceService.dataSource)

    val proxyManager: ProxyManager<String> = MySQLSelectForUpdateBasedProxyManager(sqlConfiguration)
    return Bucket4jRateLimiter(proxyManager, clock, meterRegistry)
  }

  @Provides
  @Singleton
  fun providedPruner(clock: Clock, injector: Injector, meterRegistry: MeterRegistry): RateLimitPruner {
    val dataSourceService = injector.getInstance(keyOf<DataSourceService>(qualifier))
    return MySQLBucketPruner(
      clock = clock,
      dataSource = dataSourceService.dataSource,
      idColumn = idColumn,
      meterRegistry = meterRegistry,
      stateColumn = stateColumn,
      tableName = tableName,
      pageSize = prunerPageSize,
    )
  }
}
