package com.squareup.urlshortener

import misk.config.ConfigWebModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.AdminTabModule
import misk.web.MiskWebModule
import misk.web.actions.WebActionEntry
import misk.web.proxy.WebProxyActionModule

/** Binds all service dependencies including service-specific dependencies. */
class UrlShortenerServiceModule : KAbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(UrlShortenerModule(environment))
    install(ConfigWebModule(environment))

    install(MiskWebModule())
    // Add _admin installed tabs / forwarding mappings that don't have endpoints
    install(AdminTabModule(environment))

    install(WebProxyActionModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry<CreateShortUrlWebAction>())
    multibind<WebActionEntry>().toInstance(WebActionEntry<ShortUrlWebAction>())

  }
}