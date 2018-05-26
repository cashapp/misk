package misk.moshi

import misk.inject.KAbstractModule
import misk.inject.newMultibinder

class MoshiAdapterModule(private val jsonAdapter: Any) : KAbstractModule() {
  override fun configure() {
    binder().newMultibinder<Any>(MoshiJsonAdapter::class).addBinding().toInstance(jsonAdapter)
  }
}
