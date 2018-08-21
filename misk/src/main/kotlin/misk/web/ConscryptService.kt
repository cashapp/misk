package misk.web

import com.google.common.base.Stopwatch
import com.google.common.util.concurrent.AbstractIdleService
import com.google.inject.Key
import misk.DependentService
import misk.inject.toKey
import misk.logging.getLogger
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton
import java.security.Provider as SecurityProvider

private val logger = getLogger<ConscryptService>()

/**
 * Conscrypt packages BoringSSL as a Java Security Provider. We use it for TLS.
 *
 * https://github.com/google/conscrypt
 */
@Singleton
internal class ConscryptService : AbstractIdleService(),
    DependentService,
    Provider<SecurityProvider> {
  private var conscryptProvider: SecurityProvider? = null

  override val consumedKeys = setOf<Key<*>>()
  override val producedKeys = setOf<Key<*>>(SecurityProvider::class.toKey(ForConscrypt::class))

  override fun startUp() {
    require(conscryptProvider == null)

    val stopwatch = Stopwatch.createStarted()
    conscryptProvider = Conscrypt.newProvider()
    Security.addProvider(conscryptProvider)

    logger.info("Initialized Conscrypt in $stopwatch")
  }

  override fun shutDown() {
  }

  override fun get(): SecurityProvider {
    return conscryptProvider ?: throw IllegalStateException(
        "Conscrypt not available: did you forget to start the service?")
  }
}

@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
internal annotation class ForConscrypt