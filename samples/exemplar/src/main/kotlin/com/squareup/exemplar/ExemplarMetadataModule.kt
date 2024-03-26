package com.squareup.exemplar

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.metadata.all.AllMetadataAccess
import misk.web.metadata.all.AllMetadataModule

class ExemplarMetadataModule: KAbstractModule() {
  override fun configure() {
    install(AllMetadataModule())

    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AllMetadataAccess>(
        capabilities = listOf("admin_console"),
        services = listOf()
      )
    )
  }
}

