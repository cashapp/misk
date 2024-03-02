package com.squareup.exemplar.events

import misk.ReadyService
import misk.ServiceModule
import misk.inject.KAbstractModule

class EventServiceModule : KAbstractModule() {
  override fun configure() {
    install(
      ServiceModule<EventService>()
        .enhancedBy<ReadyService>()
    )
  }
}
