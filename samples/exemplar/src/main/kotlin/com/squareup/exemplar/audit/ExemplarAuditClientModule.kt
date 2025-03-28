package com.squareup.exemplar.audit

import misk.audit.AuditClient
import misk.audit.AuditClientConfig
import misk.client.HttpClientModule
import misk.inject.KAbstractModule

class ExemplarAuditClientModule(private val config: AuditClientConfig) : KAbstractModule() {
  override fun configure() {
    bind<AuditClientConfig>().toInstance(config)
    bind<AuditClient>().to<ExemplarAuditClient>()
  }
}
