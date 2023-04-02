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
    install(WebActionModule.createWithPrefix<StaticResourceAction>("/room/"))
    multibind<StaticResourceEntry>().toInstance(
      StaticResourceEntry("/room/", "classpath:/web/")
    )

    install(WebActionModule.create<ChatWebSocketAction>())
    install(WebActionModule.create<ToggleManualHealthCheckAction>())
    multibind<HealthCheck>().to<ManualHealthCheck>()
  }
}
