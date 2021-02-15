package com.squareup.exemplar

import com.squareup.exemplar.actions.EchoFormAction
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebPostAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class ExemplarWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<EchoFormAction>())
  }
}
