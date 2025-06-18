package misk.vitess.testing

import java.time.Duration

object DefaultSettings {
  const val AUTO_APPLY_SCHEMA_CHANGES = true
  const val CONTAINER_NAME = "vitess_test_db"
  const val CONTAINER_PORT_VTGATE = 27003
  const val CONTAINER_PORT_MYSQL = 27002
  const val CONTAINER_PORT_GRPC = 27001
  const val CONTAINER_PORT_BASE = 27000
  const val DEBUG_STARTUP = false
  const val DYNAMIC_PORT: Int = 0
  const val ENABLE_DECLARATIVE_SCHEMA_CHANGES = false
  const val ENABLE_IN_MEMORY_STORAGE = false
  const val ENABLE_SCATTERS = true
  const val IN_MEMORY_STORAGE_SIZE = "1024M"
  const val KEEP_ALIVE = true
  const val LINT_SCHEMA = false
  const val MYSQL_VERSION = "8.0.36"
  const val PORT = 27003
  const val SCHEMA_DIR = "classpath:/vitess/schema"
  const val SQL_MODE: String =
    "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
  @JvmField var TRANSACTION_ISOLATION_LEVEL: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ
  @JvmField var TRANSACTION_TIMEOUT_SECONDS: Duration = Duration.ofSeconds(30)
  const val VITESS_DOCKER_NETWORK_NAME = "vitess-network"
  const val VITESS_DOCKER_NETWORK_TYPE = "bridge"
  const val VITESS_IMAGE = "vitess/vttestserver:v21.0.4-mysql80"
  const val VITESS_VERSION = 21
  const val VTCTLD_CLIENT_IMAGE = "vitess/vtctldclient:v21.0.4"
}

enum class TransactionIsolationLevel(val value: String) {
  READ_UNCOMMITTED("READ-UNCOMMITTED"),
  READ_COMMITTED("READ-COMMITTED"),
  REPEATABLE_READ("REPEATABLE-READ"),
  SERIALIZABLE("SERIALIZABLE"),
}
