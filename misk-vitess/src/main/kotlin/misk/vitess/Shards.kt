package misk.vitess

import com.google.common.base.Supplier
import misk.jdbc.DataSourceService

fun shards(dataSourceService: DataSourceService): Supplier<Set<Shard>> =
  ShardsLoader.shards(dataSourceService)
