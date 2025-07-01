package com.squareup.exemplar

import com.squareup.exemplar.actions.DownloadAFileWebAction
import com.squareup.exemplar.actions.EchoFormAction
import com.squareup.exemplar.actions.HelloWebAction
import com.squareup.exemplar.actions.HelloWebGrpcAction
import com.squareup.exemplar.actions.HelloWebPostAction
import com.squareup.exemplar.actions.HelloWebProtoAction
import com.squareup.exemplar.actions.LeaseAcquireWebAction
import com.squareup.exemplar.actions.LeaseCheckWebAction
import misk.inject.KAbstractModule
import misk.web.WebActionModule

class ExemplarWebActionsModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<HelloWebGrpcAction>())
    install(WebActionModule.create<HelloWebAction>())
    install(WebActionModule.create<HelloWebPostAction>())
    install(WebActionModule.create<EchoFormAction>())
    install(WebActionModule.create<HelloWebProtoAction>())
    install(WebActionModule.create<DownloadAFileWebAction>())
    install(WebActionModule.create<LeaseAcquireWebAction>())
    install(WebActionModule.create<LeaseCheckWebAction>())
  }
}
