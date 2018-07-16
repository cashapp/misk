package com.squareup.chat

import com.squareup.chat.actions.ChatPageAction
import com.squareup.chat.actions.ChatWebSocketAction
import com.squareup.chat.actions.ToggleManualHealthCheckAction
import com.squareup.chat.healthchecks.ManualHealthCheck
import misk.healthchecks.ClusterWideHealthModule
import misk.healthchecks.HealthCheck
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.resources.StaticResourceMapper

class ChatModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ChatPageAction>())
    install(WebActionModule.create<ChatWebSocketAction>())
    install(WebActionModule.create<ToggleManualHealthCheckAction>())
    install(ClusterWideHealthModule())
    multibind<HealthCheck>().to<ManualHealthCheck>()
    multibind<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/", "web/exemplarchat", "exemplarchat/web/build"))
  }
}
