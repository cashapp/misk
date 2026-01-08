package misk.moshi

import com.google.inject.Binder
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Types
import misk.inject.KAbstractModule

class MoshiAdapterModule @JvmOverloads constructor(private val jsonAdapter: Any, private val addLast: Boolean = false) :
  KAbstractModule() {
  override fun configure() {
    val annotation = if (addLast) MoshiJsonLastAdapter::class else MoshiJsonAdapter::class
    multibind<Any>(annotation).toInstance(jsonAdapter)
  }

  override fun binder(): Binder = super.binder().skipSources(MoshiAdapterModule::class.java)

  companion object {
    inline operator fun <reified T> invoke(adapter: JsonAdapter<T>, addLast: Boolean = false): MoshiAdapterModule {
      val jsonAdapter = JsonAdapter.Factory { type, _, _ -> if (Types.equals(type, T::class.java)) adapter else null }
      return MoshiAdapterModule(jsonAdapter, addLast)
    }
  }
}
