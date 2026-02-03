package misk.jooq.transacter

import com.google.inject.Provider
import kotlin.reflect.KClass
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.inject.keyOf
import misk.inject.toKey
import misk.jooq.JooqTransacter

/**
 * Guice module that binds a [Transacter] for jOOQ database operations.
 *
 * The [Transacter] provides a thread-safe, fluent API for configuring and executing database transactions with support
 * for read-only mode, retries, transaction isolation levels, and replica reads.
 *
 * @param writerQualifier Qualifier annotation for the writer [JooqTransacter].
 * @param defaultTransacterOptions Default transaction options applied to all transactions.
 * @param readerQualifier Qualifier annotation for the reader [JooqTransacter]. Required for [Transacter.replicaRead]
 *   support, which will throw without it.
 * @param transacterBindingQualifier Optional qualifier for the bound [Transacter]. If null, the transacter is bound
 *   without a qualifier.
 *
 * Example usage:
 * ```kotlin
 * install(JooqUnifiedTransacterModule(
 *   writerQualifier = MyDbWriter::class,
 *   readerQualifier = MyDbReader::class,
 * ))
 *
 * // Then inject and use:
 * @Inject private lateinit var transacter: Transacter
 * ```
 */
class JooqUnifiedTransacterModule
@JvmOverloads
constructor(
  private val writerQualifier: KClass<out Annotation>,
  private val defaultTransacterOptions: JooqTransacter.TransacterOptions = JooqTransacter.TransacterOptions(),
  private val readerQualifier: KClass<out Annotation>? = null,
  private val transacterBindingQualifier: KClass<out Annotation>? = null,
) : KAbstractModule() {
  override fun configure() {
    bind(Transacter::class.toKey(transacterBindingQualifier)).toProvider(getTransacterProvider()).asSingleton()
  }

  private fun getTransacterProvider(): Provider<Transacter> {
    val writerProvider = getProvider(keyOf<JooqTransacter>(writerQualifier))
    val readerProvider = readerQualifier?.let { getProvider(keyOf<JooqTransacter>(it)) }
    return Provider<Transacter> {
      val writer = writerProvider.get()
      val reader = readerProvider?.get()
      RealTransacter(defaultTransacterOptions, writer, reader)
    }
  }
}
