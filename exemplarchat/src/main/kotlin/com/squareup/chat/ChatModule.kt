package com.squareup.chat

import com.squareup.chat.actions.ChatPage
import com.squareup.chat.actions.ChatWebSocketAction
import misk.NetworkInterceptor
import misk.eventrouter.RealEventRouterModule
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.web.StaticResourceInterceptor
import misk.web.StaticResourceMapper
import misk.web.WebActionModule
import misk.web.actions.DefaultActionsModule

class ChatModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ChatPage>())
    install(WebActionModule.create<ChatWebSocketAction>())
    install(DefaultActionsModule())
    install(RealEventRouterModule())
    binder().addMultibinderBinding<StaticResourceMapper.Entry>()
        .toInstance(StaticResourceMapper.Entry("/", "web/exemplarchat", "exemplarchat/web/build"))
  }
}
