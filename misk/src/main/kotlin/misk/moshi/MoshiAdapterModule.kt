package misk.moshi

import misk.inject.KAbstractModule

class MoshiAdapterModule(private val jsonAdapter: Any) : KAbstractModule() {
  override fun configure() {
    multibind<Any>(MoshiJsonAdapter::class).toInstance(jsonAdapter)
  }
}
