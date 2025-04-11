package misk.vitess.testing

import java.time.Duration

object DefaultSettings {
  const val AUTO_APPLY_SCHEMA_CHANGES = true
  const val CONTAINER_NAME = "vitess_test_db"
  const val DEBUG_STARTUP = false
  const val ENABLE_DECLARATIVE_SCHEMA_CHANGES = false
  const val ENABLE_SCATTERS = true
  const val KEEP_ALIVE = true
  const val LINT_SCHEMA = false
  const val MYSQL_VERSION = "8.0.36"
  const val PORT = 27003
  const val SCHEMA_DIR = "classpath:/vitess/schema"
  const val SQL_MODE: String =
    "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
  @JvmField var TRANSACTION_ISOLATION_LEVEL: TransactionIsolationLevel = TransactionIsolationLevel.REPEATABLE_READ
  @JvmField var TRANSACTION_TIMEOUT_SECONDS: Duration = Duration.ofSeconds(30)
  const val VITESS_DOCKER_NETWORK = "vitess-network"
  const val VITESS_IMAGE = "vitess/vttestserver:v19.0.9-mysql80"
  const val VITESS_VERSION = 19
  const val VTCTLD_CLIENT_IMAGE = "vitess/vtctldclient:v21.0.2"
}

enum class TransactionIsolationLevel(val value: String) {
  READ_UNCOMMITTED("READ-UNCOMMITTED"),
  READ_COMMITTED("READ-COMMITTED"),
  REPEATABLE_READ("REPEATABLE-READ"),
  SERIALIZABLE("SERIALIZABLE"),
}
