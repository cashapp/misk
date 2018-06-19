package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.inject.newMultibinder
import misk.inject.to
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.wire.WireMessageAdapter
import javax.inject.Singleton

class MoshiModule : KAbstractModule() {
  override fun configure() {
    val factoryBinder = binder().newMultibinder<JsonAdapter.Factory>(MoshiJsonAdapter::class)
    factoryBinder.addBinding().to<WireMessageAdapter.Factory>()

    val adapterBinder = binder().newMultibinder<Any>(MoshiJsonAdapter::class)
    adapterBinder.addBinding().toInstance(ByteStringAdapter)
  }

  @Provides
  @Singleton
  fun provideMoshi(
    @MoshiJsonAdapter jsonAdapters: List<Any>,
    @MoshiJsonAdapter jsonAdapterFactories: List<JsonAdapter.Factory>
  ): Moshi {
    val builder = Moshi.Builder()
    jsonAdapters.forEach { builder.add(it) }
    jsonAdapterFactories.forEach { builder.add(it) }
    return builder.build()
  }
}
