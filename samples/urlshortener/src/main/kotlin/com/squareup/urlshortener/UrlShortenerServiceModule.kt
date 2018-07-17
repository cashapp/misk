package com.squareup.urlshortener

import misk.config.ConfigWebModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.web.MiskWebModule
import misk.web.WebActionEntry
import misk.web.WebProxyActionModule

/** Binds all service dependencies including service-specific dependencies. */
class UrlShortenerServiceModule : KAbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(UrlShortenerModule(environment))
    install(ConfigWebModule())

    install(MiskWebModule())
    install(WebProxyActionModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry(CreateShortUrlWebAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(ShortUrlWebAction::class))
  }
}
