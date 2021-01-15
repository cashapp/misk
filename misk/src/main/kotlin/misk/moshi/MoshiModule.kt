package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.inject.KAbstractModule
import misk.moshi.adapters.BigDecimalAdapter
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.time.InstantAdapter
import misk.moshi.time.LocalDateAdapter
import misk.moshi.wire.WireMessageAdapter
import java.util.Date
import javax.inject.Singleton

internal class MoshiModule : KAbstractModule() {
  override fun configure() {
    install(MoshiAdapterModule(WireMessageAdapter.Factory()))
    install(MoshiAdapterModule(ByteStringAdapter))
    install(MoshiAdapterModule<Date>(Rfc3339DateJsonAdapter()))
    install(MoshiAdapterModule(InstantAdapter))
    install(MoshiAdapterModule(BigDecimalAdapter))
    install(MoshiAdapterModule(LocalDateAdapter))
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

    // Install last so that user adapters take precedence.
    builder.add(KotlinJsonAdapterFactory())

    return builder.build()
  }
}
