package misk.flags

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

interface Flag<out T> {
  val name: String
  val description: String
  fun get(): T?
}

class JsonFlag<out T : Any> internal constructor(
  private val stringFlag: Flag<String>,
  private val adapter: JsonAdapter<T>
) : Flag<T> {
  override val name = stringFlag.name
  override val description = stringFlag.description
  override fun get(): T? = stringFlag.get()?.let { adapter.fromJson(it) }
}

open class Flags internal constructor(val name: String, val context: Context) {
  data class Context(
    val prefix: String,
    val moshi: Moshi,
    val flagStore: FlagStore
  )

  class StringFlag(
    private val description: String,
    private val name: String? = null,
    private val default: String = ""
  ) {
    operator fun provideDelegate(thisRef: Flags, prop: KProperty<*>):
      ReadOnlyProperty<Flags, String> {
        val propertyFlagName = propertyFlagName(thisRef, name, prop)
        val flag = thisRef.context.flagStore.registerFlag<String>(propertyFlagName, description)
        return Property(flag, default)
      }

    private class Property(val flag: misk.flags.Flag<String>, val default: String) :
      ReadOnlyProperty<Flags, String> {
      override fun getValue(thisRef: Flags, property: KProperty<*>): String =
        flag.get() ?: default
    }
  }

  class IntFlag(
    private val description: String,
    private val name: String? = null,
    private val default: Int = 0
  ) {
    operator fun provideDelegate(thisRef: Flags, prop: KProperty<*>):
      ReadOnlyProperty<Flags, Int> {
        val propertyFlagName = propertyFlagName(thisRef, name, prop)
        val flag = thisRef.context.flagStore.registerFlag<Int>(propertyFlagName, description)
        return Property(flag, default)
      }

    private class Property(
      val flag: misk.flags.Flag<Int>,
      val default: Int
    ) : ReadOnlyProperty<Flags, Int> {
      override fun getValue(thisRef: Flags, property: KProperty<*>): Int =
        flag.get() ?: default
    }
  }

  class BooleanFlag(
    private val description: String,
    private val name: String? = null,
    private val default: Boolean = false
  ) {
    operator fun provideDelegate(thisRef: Flags, prop: KProperty<*>):
      ReadOnlyProperty<Flags, Boolean> {
        val propertyFlagName = propertyFlagName(thisRef, name, prop)
        val flag = thisRef.context.flagStore.registerFlag<Boolean>(propertyFlagName, description)
        return Property(flag, default)
      }

    private class Property(val flag: misk.flags.Flag<Boolean>, val default: Boolean) :
      ReadOnlyProperty<Flags, Boolean> {
      override fun getValue(thisRef: Flags, property: KProperty<*>): Boolean =
        flag.get() ?: default
    }
  }

  class DoubleFlag(
    private val description: String,
    private val name: String? = null,
    private val default: Double = 0.0
  ) {
    operator fun provideDelegate(thisRef: Flags, prop: KProperty<*>):
      ReadOnlyProperty<Flags, Double> {
        val propertyFlagName = propertyFlagName(thisRef, name, prop)
        val flag = thisRef.context.flagStore.registerFlag<Double>(propertyFlagName, description)
        return Property(flag, default)
      }

    private class Property(val flag: misk.flags.Flag<Double>, val default: Double) :
      ReadOnlyProperty<Flags, Double> {
      override fun getValue(thisRef: Flags, property: KProperty<*>): Double =
        flag.get() ?: default
    }
  }

  inline fun <reified T : Any> JsonFlag(
    description: String,
    name: String? = null,
    default: T? = null
  ) = JsonFlagInternal(T::class, description, name, default)

  class JsonFlagInternal<T : Any>(
    private val kclass: KClass<T>,
    private val description: String,
    private val name: String? = null,
    private val default: T? = null
  ) {
    operator fun provideDelegate(thisRef: Flags, prop: KProperty<*>):
      ReadOnlyProperty<Flags, T?> {
        val adapter = thisRef.context.moshi.adapter(kclass.java)
        val propertyFlagName = propertyFlagName(thisRef, name, prop)
        val flag = thisRef.context.flagStore.registerFlag<String>(propertyFlagName, description)
        return Property(flag, adapter, default)
      }

    private class Property<T : Any>(
      val flag: misk.flags.Flag<String>,
      val adapter: JsonAdapter<T>,
      val default: T?
    ) : ReadOnlyProperty<Flags, T?> {
      override fun getValue(thisRef: Flags, property: KProperty<*>): T? =
        flag.get()?.let { adapter.fromJson(it) } ?: default
    }
  }

  companion object {
    internal fun propertyFlagName(
      thisRef: Flags,
      explicitName: String?,
      prop: KProperty<*>
    ): String {
      val name = explicitName ?: prop.name
      return if (thisRef.context.prefix.isEmpty()) {
        "${thisRef.name}.$name"
      } else {
        "${thisRef.context.prefix}.${thisRef.name}.$name"
      }
    }
  }
}
