package com.squareup.urlshortener

import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.AdminTabModule
import misk.web.MiskWebModule
import misk.web.actions.WebActionEntry

/** Binds all service dependencies including service-specific dependencies. */
class UrlShortenerServiceModule : KAbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(UrlShortenerModule(environment))

    install(MiskWebModule())
    // Add _admin installed tabs / forwarding mappings that don't have endpoints
    install(AdminTabModule(environment))

    multibind<WebActionEntry>().toInstance(WebActionEntry<CreateShortUrlWebAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<ShortUrlWebAction>())

  }
}