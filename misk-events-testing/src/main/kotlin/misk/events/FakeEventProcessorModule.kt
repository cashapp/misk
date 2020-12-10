package misk.events

import misk.inject.KAbstractModule

@Deprecated("This API is no longer supported and replaced by the new event system's client library")
object FakeEventProcessorModule : KAbstractModule() {
  override fun configure() {
    newMapBinder<Topic, Consumer.Handler>() // Zero event consumers.
    bind<Producer>().to<FakeEventProcessor>()
  }
}
