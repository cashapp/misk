package com.squareup.chat

import com.squareup.chat.actions.ChatPageAction
import com.squareup.chat.actions.ChatWebSocketAction
import com.squareup.chat.actions.ToggleManualHealthCheckAction
import com.squareup.chat.healthchecks.ManualHealthCheck
import misk.healthchecks.ClusterWideHealthModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.web.WebActionEntry
import misk.web.resources.StaticResourceMapper

class ChatModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry(ChatPageAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(ChatWebSocketAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(ToggleManualHealthCheckAction::class))
    install(ClusterWideHealthModule())
    multibind<HealthCheck>().to<ManualHealthCheck>()
    multibind<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/", "web/exemplarchat", "exemplarchat/web/build"))
  }
}
