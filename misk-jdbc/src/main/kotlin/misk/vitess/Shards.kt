package misk.vitess

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import misk.jdbc.DataSourceService
import misk.jdbc.map
import java.sql.SQLRecoverableException
import java.util.concurrent.TimeUnit

fun shards(dataSourceService: DataSourceService): Supplier<Set<Shard>> =
  Suppliers.memoizeWithExpiration({
    if (!dataSourceService.config().type.isVitess) {
      Shard.SINGLE_SHARD_SET
    } else {
      dataSourceService.get().connection.use { connection ->
        connection.createStatement().use { s ->
          val shards = s.executeQuery("SHOW VITESS_SHARDS")
            .map { rs -> Shard.parse(rs.getString(1)) }
            .toSet()
          if (shards.isEmpty()) {
            throw SQLRecoverableException("Failed to load list of shards")
          }
          shards
        }
      }
    }
  }, 5, TimeUnit.MINUTES)
