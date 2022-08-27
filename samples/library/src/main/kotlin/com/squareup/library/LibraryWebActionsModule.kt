package com.squareup.library

import com.squareup.library.actions.EchoFormAction
import com.squareup.library.actions.HelloWebAction
import com.squareup.library.actions.HelloWebPostAction
import com.squareup.library.actions.HelloWebProtoAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class LibraryWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<EchoFormAction>())
    install(WebActionModule.create<HelloWebProtoAction>())
  }
}
