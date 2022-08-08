package misk.hibernate

import com.google.inject.Injector
import misk.ServiceModule
import misk.concurrent.ExecutorServiceFactory
import misk.healthchecks.HealthCheck
import misk.hibernate.ReflectionQuery.QueryLimitsConfig
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.setOfType
import misk.inject.toKey
import misk.inject.typeLiteral
import misk.jdbc.DataSourceClusterConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.DataSourceDecorator
import misk.jdbc.DataSourceService
import misk.jdbc.DataSourceType
import misk.jdbc.DatabasePool
import misk.jdbc.JdbcModule
import misk.jdbc.RealDatabasePool
import misk.jdbc.SchemaMigratorService
import misk.web.exceptions.ExceptionMapperModule
import org.hibernate.SessionFactory
import org.hibernate.event.spi.EventType
import org.hibernate.exception.ConstraintViolationException
import java.time.Clock
import javax.inject.Inject
import javax.inject.Provider
import javax.persistence.OptimisticLockException
import javax.sql.DataSource
import kotlin.reflect.KClass

/**
 * Binds database connectivity for a qualified data source. This binds the following public types:
 *
 *  * @Qualifier [misk.jdbc.DataSourceConfig]
 *  * @Qualifier [SessionFactory]
 *  * @Qualifier [Transacter]
 *  * [Query.Factory] (with no qualifier)
 *
 * This also registers services to connect to the database ([SessionFactoryService]) and to verify
 * that the schema is up-to-date ([SchemaMigratorService]).
 */

private const val MAX_MAX_ROWS = 10_000
private const val ROW_COUNT_ERROR_LIMIT = 3000
private const val ROW_COUNT_WARNING_LIMIT = 2000

