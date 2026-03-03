package com.squareup.chat

import com.squareup.chat.actions.ChatWebSocketAction
import com.squareup.chat.actions.ToggleManualHealthCheckAction
import com.squareup.chat.healthchecks.ManualHealthCheck
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class ChatModule : KAbstractModule() {
  override fun configure() {
    // TODO(adrw) finish testing this with the new StaticResourceAction
    install(WebActionModule.createWithPrefix<StaticResourceAction>("/room/"))
    multibind<StaticResourceEntry>().toInstance(
        StaticResourceEntry("/room/", "classpath:/web/index.html"))

    install(WebActionModule.create<ChatWebSocketAction>())
    install(WebActionModule.create<ToggleManualHealthCheckAction>())
    multibind<HealthCheck>().to<ManualHealthCheck>()
  }
}
