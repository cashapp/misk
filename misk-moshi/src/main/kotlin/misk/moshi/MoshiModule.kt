package misk.moshi

import com.google.inject.Provides
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import misk.inject.KAbstractModule
import misk.moshi.adapters.BigDecimalAdapter
import misk.moshi.okio.ByteStringAdapter
import misk.moshi.time.DurationAdapter
import misk.moshi.time.InstantAdapter
import misk.moshi.time.LocalDateAdapter
import wisp.moshi.buildMoshi
import jakarta.inject.Singleton
import misk.moshi.time.OffsetDateTimeAdapter
import wisp.moshi.ProviderJsonAdapterFactory
import java.util.Date
import com.squareup.wire.WireJsonAdapterFactory as WireOnlyJsonAdapterFactory
import misk.moshi.wire.WireMessageAdapter as MiskOnlyMessageAdapter

/**
 * For service setup, prefer to install [misk.MiskCommonServiceModule] over installing [MoshiModule]
 * directly.
 */
class MoshiModule @JvmOverloads constructor(
  private val useWireToRead: Boolean = false,
  private val useWireToWrite: Boolean = false
) : KAbstractModule() {
  override fun configure() {
    newMultibinder<Any>(MoshiJsonAdapter::class)
    newMultibinder<Any>(MoshiJsonLastAdapter::class)
    
    val wireFactory = WireOnlyJsonAdapterFactory()
    val miskFactory = MiskOnlyMessageAdapter.Factory()
    install(
      MoshiAdapterModule(
        MigratingJsonAdapterFactory(
          reader = if (useWireToRead) wireFactory else miskFactory,
          writer = if (useWireToWrite) wireFactory else miskFactory
        ),
        addLast = true,
      )
    )

    install(MoshiAdapterModule<Date>(Rfc3339DateJsonAdapter()))
    defaultMoshiAdapters.forEach {
      install(MoshiAdapterModule(it))
    }
  }

  @Provides
  @Singleton
  fun provideMoshi(
    @MoshiJsonAdapter jsonAdapters: List<Any>,
    @MoshiJsonLastAdapter jsonLastAdapters: List<Any>,
  ): Moshi {
    return buildMoshi(jsonAdapters, jsonLastAdapters)
  }

  companion object {
    val defaultMoshiAdapters = listOf(
      ByteStringAdapter,
      DurationAdapter,
      InstantAdapter,
      BigDecimalAdapter,
      LocalDateAdapter,
      OffsetDateTimeAdapter,
      ProviderJsonAdapterFactory(),
    )
  }
}
