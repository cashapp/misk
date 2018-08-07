package com.squareup.exemplar

import com.squareup.exemplar.actions.EchoFormAction
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebPostAction
import misk.inject.KAbstractModule
import misk.web.actions.WebActionEntry

class ExemplarModule : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<HelloWebAction>())
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<HelloWebPostAction>())
    multibind<WebActionEntry>().toInstance(
        WebActionEntry<EchoFormAction>())
  }
}
