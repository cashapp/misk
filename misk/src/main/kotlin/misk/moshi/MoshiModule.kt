package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.wire.WireMessageAdapter
import javax.inject.Singleton

class MoshiModule : KAbstractModule() {
  override fun configure() {
    binder().addMultibinderBinding<JsonAdapter.Factory>().to<WireMessageAdapter.Factory>()
    binder().addMultibinderBinding<JsonAdapter.Factory>().to<ByteStringAdapter.Factory>()
  }

  @Provides
  @Singleton
  fun provideMoshi(jsonAdapterFactories: List<JsonAdapter.Factory>): Moshi {
    val builder = Moshi.Builder()
    jsonAdapterFactories.forEach { builder.add(it) }
    return builder.build()
  }
}
