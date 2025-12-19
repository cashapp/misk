package com.squareup.exemplar

import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.dl
import kotlinx.html.dt
import kotlinx.html.p
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.tailwind.components.AlertError
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataModule
import misk.web.metadata.MetadataProvider
import misk.web.metadata.all.AllMetadataAccess
import misk.web.metadata.all.AllMetadataModule

class ExemplarMetadataModule : KAbstractModule() {
  override fun configure() {
    install(AllMetadataModule())
    install(MetadataModule(DinoMetadataProvider()))

    multibind<AccessAnnotationEntry>()
      .toInstance(AccessAnnotationEntry<AllMetadataAccess>(capabilities = listOf("admin_console"), services = listOf()))
  }
}

data class DinoMetadata(val dinos: List<String>) :
  Metadata(metadata = dinos, descriptionString = "Dinos in the world") {
  override fun contentBlock(tagConsumer: TagConsumer<*>): TagConsumer<*> =
    tagConsumer.apply {
      AlertError("Dinos are on the loose!")
      p { +"The following dinos are in the world:" }
      p { +dinos.joinToString(", ") }

      dl("mx-auto grid grid-cols-1 gap-px bg-gray-900/5 sm:grid-cols-2 lg:grid-cols-4") {
        div("flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8") {
          this@dl.dt("text-sm font-medium leading-6 text-gray-500") { +"""Revenue""" }
          this@dl.dt("text-xs font-medium text-gray-700") { +"""+4.75%""" }
          this@dl.dt("w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900") {
            +"""$405,091.00"""
          }
        }
        div("flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8") {
          this@dl.dt("text-sm font-medium leading-6 text-gray-500") { +"""Overdue invoices""" }
          this@dl.dt("text-xs font-medium text-rose-600") { +"""+54.02%""" }
          this@dl.dt("w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900") {
            +"""$12,787.00"""
          }
        }
        div("flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8") {
          this@dl.dt("text-sm font-medium leading-6 text-gray-500") { +"""Outstanding invoices""" }
          this@dl.dt("text-xs font-medium text-gray-700") { +"""-1.39%""" }
          this@dl.dt("w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900") {
            +"""$245,988.00"""
          }
        }
        div("flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8") {
          this@dl.dt("text-sm font-medium leading-6 text-gray-500") { +"""Expenses""" }
          this@dl.dt("text-xs font-medium text-rose-600") { +"""+10.18%""" }
          this@dl.dt("w-full flex-none text-3xl font-medium leading-10 tracking-tight text-gray-900") {
            +"""$30,156.00"""
          }
        }
      }
    }
}

class DinoMetadataProvider : MetadataProvider<Metadata> {
  override val id: String = "dino"

  override fun get() = DinoMetadata(dinos = listOf("T-Rex", "Velociraptor", "Stegosaurus"))
}
