package com.squareup.exemplar

import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataModule
import misk.web.metadata.MetadataProvider
import misk.web.metadata.all.AllMetadataAccess
import misk.web.metadata.all.AllMetadataModule

class ExemplarMetadataModule : KAbstractModule() {
  override fun configure() {
    install(AllMetadataModule())
    install(MetadataModule(DinoMetadataProvider()))

    multibind<AccessAnnotationEntry>().toInstance(
      AccessAnnotationEntry<AllMetadataAccess>(
        capabilities = listOf("admin_console"),
        services = listOf()
      )
    )
  }
}

class DinoMetadataProvider : MetadataProvider<Metadata> {
  override val id: String = "dino"

  override fun get() = Metadata(
    metadata = listOf("t-rex", "stegosaurus", "triceratops")
  )
}
