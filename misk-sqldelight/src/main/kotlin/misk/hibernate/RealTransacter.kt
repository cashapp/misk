package misk.hibernate

import com.google.common.base.Supplier
import com.google.common.base.Suppliers
import misk.backoff.ExponentialBackoff
import misk.concurrent.ExecutorServiceFactory
import misk.jdbc.CheckDisabler
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceType
import misk.jdbc.map
import misk.jdbc.uniqueString
import misk.vitess.Destination
import misk.vitess.Keyspace
import misk.vitess.Shard
import misk.vitess.Shard.Companion.SINGLE_SHARD_SET
import misk.vitess.TabletType
import org.hibernate.FlushMode
import org.hibernate.SessionFactory
import org.hibernate.StaleObjectStateException
import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.LockAcquisitionException
import wisp.logging.getLogger
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.sql.SQLIntegrityConstraintViolationException
import java.sql.SQLRecoverableException
import java.sql.SQLTransientException
import java.time.Duration
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.persistence.OptimisticLockException
import kotlin.reflect.KClass

private val logger = getLogger<RealTransacter>()

// Check was moved to misk.jdbc, keeping a type alias to prevent compile breakage for usages.
typealias Check = misk.jdbc.Check

