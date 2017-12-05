package com.squareup.exemplar

import com.google.inject.AbstractModule
import misk.web.WebActionModule
import misk.web.actions.InternalErrorAction
import misk.web.actions.NotFoundAction

class ExemplarModule : AbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<InternalErrorAction>())
    install(WebActionModule.create<NotFoundAction>())
  }
}
