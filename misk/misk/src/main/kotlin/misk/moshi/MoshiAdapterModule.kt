package misk.moshi

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import misk.inject.KAbstractModule
import java.lang.reflect.Type

class MoshiAdapterModule(private val jsonAdapter: Any) : KAbstractModule() {
  override fun configure() {
    multibind<Any>(MoshiJsonAdapter::class).toInstance(jsonAdapter)
  }

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
