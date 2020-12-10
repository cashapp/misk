package misk.events

import misk.inject.KAbstractModule

object FakeEventProcessorModule : KAbstractModule() {
  override fun configure() {
    newMapBinder<Topic, Consumer.Handler>() // Zero event consumers.
    bind<Producer>().to<FakeEventProcessor>()
  }
}
