package com.squareup.exemplar

import com.google.inject.AbstractModule
import com.squareup.exemplar.actions.EchoFormAction
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebPostAction
import misk.web.WebActionModule
import misk.web.actions.DefaultActionsModule

class ExemplarModule : AbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<EchoFormAction>())
    install(DefaultActionsModule())
  }
}
