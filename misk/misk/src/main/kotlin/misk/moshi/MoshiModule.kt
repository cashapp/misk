package misk.moshi

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.multibindings.Multibinder
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Singleton

class MoshiModule : AbstractModule() {
  override fun configure() {
    Multibinder.newSetBinder(binder(), JsonAdapter.Factory::class.java)
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
