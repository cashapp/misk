package misk.moshi

import com.google.inject.Binder
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import misk.inject.KAbstractModule
import java.lang.reflect.Type

class MoshiAdapterModule @JvmOverloads constructor(
  private val jsonAdapter: Any,
  private val addLast: Boolean = false,
) : KAbstractModule() {
  override fun configure() {
    if (addLast) multibind<Any>(MoshiJsonLastAdapter::class).toInstance(jsonAdapter)
    else multibind<Any>(MoshiJsonAdapter::class).toInstance(jsonAdapter)
  }

  override fun binder(): Binder = super.binder().skipSources(MoshiAdapterModule::class.java)

  companion object {
    inline operator fun <reified T> invoke(adapter: JsonAdapter<T>): MoshiAdapterModule =
      MoshiAdapterModule(object : JsonAdapter.Factory {
        override fun create(
          type: Type,
          annotations: Set<Annotation>,
          moshi: Moshi
        ): JsonAdapter<*>? = if (Types.equals(type, T::class.java)) adapter else null
      })
  }
}
