package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.wire.WireMessageAdapter
import javax.inject.Singleton

class MoshiModule : KAbstractModule() {
  override fun configure() {
    multibind<JsonAdapter.Factory>(MoshiJsonAdapter::class)
        .to<WireMessageAdapter.Factory>()

    multibind<Any>(MoshiJsonAdapter::class).toInstance(ByteStringAdapter)
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
