package com.squareup.exemplar

import com.google.inject.AbstractModule
import misk.metrics.web.MetricsJsonAction
import misk.web.WebActionModule
import misk.web.actions.InternalErrorAction
import misk.web.actions.LivenessCheckAction
import misk.web.actions.ReadinessCheckAction
import misk.admin.actions.AdminStaticResourceAction
import misk.admin.actions.MiskIndexAction

class ExemplarModule : AbstractModule() {
    override fun configure() {
        install(WebActionModule.create<HelloWebAction>())
        install(WebActionModule.create<HelloWebPostAction>())
        install(WebActionModule.create<MetricsJsonAction>())
        install(WebActionModule.create<InternalErrorAction>())
        install(WebActionModule.create<ReadinessCheckAction>())
        install(WebActionModule.create<LivenessCheckAction>())
        install(WebActionModule.create<MiskIndexAction>())
        install(WebActionModule.create<AdminStaticResourceAction>())
//        install(WebActionModule.create<NotFoundAction>())
    }
}
