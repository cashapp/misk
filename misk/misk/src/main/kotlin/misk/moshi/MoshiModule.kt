package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.wire.WireMessageAdapter
import javax.inject.Singleton

internal class MoshiModule : KAbstractModule() {
  override fun configure() {
    multibind<Any>(MoshiJsonAdapter::class).to<WireMessageAdapter.Factory>()
    multibind<Any>(MoshiJsonAdapter::class).toInstance(ByteStringAdapter)
  }

  @Provides
  @Singleton
  fun provideMoshi(
    @MoshiJsonAdapter jsonAdapters: List<Any>
  ): Moshi {
    val builder = Moshi.Builder()

    jsonAdapters.forEach { jsonAdapter ->
      when (jsonAdapter) {
        is JsonAdapter.Factory -> builder.add(jsonAdapter)
        else -> builder.add(jsonAdapter)
      }
    }

    return builder.build()
  }
}
