package com.squareup.chat

import com.squareup.chat.actions.ChatWebSocketAction
import com.squareup.chat.actions.ToggleManualHealthCheckAction
import com.squareup.chat.healthchecks.ManualHealthCheck
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry
import misk.web.resources.StaticResourceAction
import misk.web.resources.StaticResourceEntry

class ChatModule : KAbstractModule() {
  override fun configure() {
    // TODO(adrw) finish testing this with the new StaticResourceAction
    multibind<WebActionEntry>().toInstance(WebActionEntry<StaticResourceAction>("/room/"))
    multibind<StaticResourceEntry>().toInstance(
        StaticResourceEntry("/room/", "classpath:/web/index.html"))

    multibind<WebActionEntry>().toInstance(WebActionEntry<ChatWebSocketAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<ToggleManualHealthCheckAction>())
    multibind<HealthCheck>().to<ManualHealthCheck>()
  }
}
