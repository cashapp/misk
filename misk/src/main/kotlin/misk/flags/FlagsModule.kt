package misk.flags

import com.google.inject.TypeLiteral
import com.google.inject.name.Names
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.inject.parameterizedType
import javax.inject.Inject
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Installs support for flag. Applications should inherit from this module and
 * override [configureFlags], calling the various bindXXXFlags methods to make flag
 * variables available
 */
abstract class FlagsModule : KAbstractModule() {
  override fun configure() {
    requireBinding(FlagStore::class.java)
    configureFlags()
  }

  protected abstract fun configureFlags()

  inline fun <reified T : Any> bindFlag(
    name: String,
    description: String,
    qualifier: Annotation = Names.named(name)
  ) {
    bindFlag(name, description, T::class, qualifier)
  }

  fun <T : Any> bindFlag(
    name: String,
    description: String,
    type: KClass<T>,
    qualifier: Annotation = Names.named(name)
  ) {
    bind(flagTypeLiteral(type))
      .annotatedWith(qualifier)
      .toProvider(FlagProvider(type, name, description))
      .asEagerSingleton()
  }

  inline fun <reified T : Flags> bindFlags(prefix: String = "", qualifier: Annotation? = null) =
    bindFlags(T::class, prefix, qualifier)

  fun <T : Flags> bindFlags(
    type: KClass<T>,
    prefix: String = "",
    qualifier: Annotation? = null
  ) {
    val actualQualifier = qualifier ?: if (!prefix.isEmpty()) Names.named(prefix) else null
    val constructor = type.constructors.firstOrNull {
      it.parameters.size == 1 && it.parameters[0].type.classifier == Flags.Context::class
    } ?: throw IllegalArgumentException(
      "$type has no single argument constructor taking a Flags.Context"
    )

    if (actualQualifier == null) {
      bind(type.java)
        .toProvider(FlagsProvider(constructor, prefix))
        .asEagerSingleton()
    } else {
      bind(type.java)
        .annotatedWith(actualQualifier)
        .toProvider(FlagsProvider(constructor, prefix))
        .asEagerSingleton()
    }
  }

  inline fun <reified T : Any> bindJsonFlag(
    name: String,
    description: String,
    qualifier: Annotation = Names.named(name)
  ) = bindJsonFlag(T::class, name, description, qualifier)

  fun <T : Any> bindJsonFlag(
    type: KClass<T>,
    name: String,
    description: String,
    a: Annotation = Names.named(name)
  ) {
    val flagType = parameterizedType<JsonFlag<T>>(type.java)

    @Suppress("UNCHECKED_CAST")
    val flagTypeLiteral = TypeLiteral.get(flagType) as TypeLiteral<JsonFlag<T>>
    bind(flagTypeLiteral)
      .annotatedWith(a)
      .toProvider(JsonFlagProvider(type, name, description))
      .asEagerSingleton()
  }

  private class FlagProvider<T : Any>(
    val type: KClass<T>,
    val name: String,
    val description: String
  ) : Provider<Flag<T>> {
    @Inject
    lateinit var flagStore: FlagStore

    override fun get(): Flag<T> {
      return flagStore.registerFlag(name, description, type)
    }
  }

  private class JsonFlagProvider<T : Any>(
    val type: KClass<T>,
    val name: String,
    val description: String
  ) : Provider<JsonFlag<T>> {
    @Inject
    lateinit var flagStore: FlagStore

    @Inject
    lateinit var moshi: Moshi

    override fun get(): JsonFlag<T> {
      val stringFlag = flagStore.registerFlag<String>(name, description)
      val adapter = moshi.adapter(type.java)
      return JsonFlag(stringFlag, adapter)
    }
  }

  private class FlagsProvider<T : Any>(
    val constructor: KFunction<T>,
    val prefix: String
  ) : Provider<T> {
    @Inject
    lateinit var flagStore: FlagStore

    @Inject
    lateinit var moshi: Moshi

    override fun get(): T {
      val context = Flags.Context(prefix = prefix, flagStore = flagStore, moshi = moshi)
      return constructor.call(context)
    }
  }

  companion object {
    private inline fun <reified T : Any> flagTypeLiteral(): TypeLiteral<Flag<T>> =
      flagTypeLiteral(T::class)

    private fun <T : Any> flagTypeLiteral(kclass: KClass<T>): TypeLiteral<Flag<T>> {
      val flagType = parameterizedType<Flag<T>>(kclass.java)

      @Suppress("UNCHECKED_CAST")
      return TypeLiteral.get(flagType) as TypeLiteral<Flag<T>>
    }
  }
}
