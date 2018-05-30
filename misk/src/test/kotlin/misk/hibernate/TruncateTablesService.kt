package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import org.hibernate.SessionFactory
import javax.inject.Provider
import kotlin.reflect.KClass

/**
 * Truncate tables before running each test.
 *
 * We truncate _before_ tests because that way we always have a clean slate, even if a preceding
 * test wasn't able to clean up after itself.
 */
// TODO(jwilson): promote this to the misk testing library.
class TruncateTablesService(
  qualifier: KClass<out Annotation>,
  val sessionFactoryProvider: Provider<SessionFactory>,
  val startUpStatements: List<String> = listOf(),
  val shutDownStatements: List<String> = listOf()
) : AbstractIdleService(), DependentService {
  override val consumedKeys = setOf<Key<*>>(SchemaMigratorService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>()

  override fun startUp() {
    // TODO(jwilson): add an option to automatically truncate all tables by default.

    sessionFactoryProvider.get().openSession().doWork { connection ->
      for (s in startUpStatements) {
        connection.createStatement().use { statement ->
          statement.execute(s)
        }
      }
    }
  }

  override fun shutDown() {
    sessionFactoryProvider.get().openSession().doWork { connection ->
      for (s in shutDownStatements) {
        connection.createStatement().use { statement ->
          statement.execute(s)
        }
      }
    }
  }
}