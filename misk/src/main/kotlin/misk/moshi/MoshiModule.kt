package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import javax.inject.Singleton

class MoshiModule : KAbstractModule() {
  override fun configure() {
    newSetBinder<JsonAdapter.Factory>()
  }

  @Provides
  @Singleton
  fun provideMoshi(
      jsonAdapterFactories: MutableSet<JsonAdapter.Factory>
  ): Moshi {
    val builder = Moshi.Builder()
    for (jsonAdapterFactory in jsonAdapterFactories) {
      builder.add(jsonAdapterFactory)
    }
    return builder.build()
  }
}
