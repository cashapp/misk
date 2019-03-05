package com.squareup.exemplar

import com.squareup.exemplar.actions.EchoFormAction
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebPostAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class ExemplarModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.forAction<HelloWebAction>())
    install(WebActionModule.forAction<HelloWebPostAction>())
    install(WebActionModule.forAction<EchoFormAction>())
  }
}
