package com.squareup.chat

import com.google.inject.AbstractModule
import misk.web.WebActionModule
import misk.web.actions.DefaultActionsModule

class ChatModule : AbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ChatWebSocketAction>())
    install(DefaultActionsModule())
  }
}
