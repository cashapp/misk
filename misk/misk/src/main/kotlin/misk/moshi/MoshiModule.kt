package misk.moshi

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.squareup.moshi.Moshi
import javax.inject.Singleton

class MoshiModule : AbstractModule() {
  override fun configure() {}

  @Provides
  @Singleton
  fun provideMoshi(): Moshi {
    return Moshi.Builder().build()
  }
}
