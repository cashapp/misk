package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.environment.Environment
import misk.inject.toKey
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

@Singleton
class SchemaValidationService internal constructor(
  qualifier: KClass<out Annotation>,
  private val environment: Environment,
  private val schemaValidationProvider: Provider<SchemaValidation> // Lazy!
) : AbstractIdleService(), DependentService {

  override val consumedKeys = setOf<Key<*>>(SessionFactoryService::class.toKey(qualifier))
  override val producedKeys = setOf<Key<*>>(SchemaValidationService::class.toKey(qualifier))

  override fun startUp() {
    val schemaValidation = schemaValidationProvider.get()
    schemaValidation.runValidation()
  }

  override fun shutDown() {
  }
}
