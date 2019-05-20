package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import misk.jdbc.DataSourceConfig
import java.util.Collections
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
internal class SchemaValidatorService internal constructor(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryServiceProvider: Provider<SessionFactoryService>,
  private val transacterProvider: Provider<Transacter>,
  private val config: DataSourceConfig
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(
      SchemaMigratorService::class.toKey(qualifier),
      SessionFactoryService::class.toKey(qualifier)
  )
  override val producedKeys = setOf<Key<*>>(SchemaValidatorService::class.toKey(qualifier))

  override fun startUp() {
    synchronized(this) {
      if (validated.contains(qualifier)) {
        return
      }
      val validator = SchemaValidator()
      val sessionFactoryService = sessionFactoryServiceProvider.get()
      validator.validate(transacterProvider.get(), sessionFactoryService.hibernateMetadata)
      validated.add(qualifier)
    }
  }

  override fun shutDown() {
  }

  companion object {
    /** Make sure we only validate each database once. It can be quite slow sometimes. */
    private val validated = Collections.synchronizedSet(HashSet<KClass<out Annotation>>())
  }
}
