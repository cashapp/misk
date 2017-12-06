package com.squareup.exemplar

import com.google.inject.AbstractModule
import misk.config.ConfigModule

class ExemplarConfigModule : AbstractModule() {
  override fun configure() {
    install(ConfigModule.create<ExemplarConfig>("exemplar"))
  }
}
