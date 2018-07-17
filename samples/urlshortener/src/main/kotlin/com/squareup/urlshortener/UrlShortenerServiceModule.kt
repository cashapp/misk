package com.squareup.urlshortener

import com.google.inject.AbstractModule
import misk.config.ConfigWebModule
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.tokens.TokenGeneratorModule
import misk.web.WebActionEntry
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.WebProxyInterceptorModule

/** Binds all service dependencies including service-specific dependencies. */
class UrlShortenerServiceModule : KAbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(UrlShortenerModule(environment))
    install(ConfigWebModule())
    install(TokenGeneratorModule())

    install(WebModule())
    install(WebProxyInterceptorModule())
    multibind<WebActionEntry>().toInstance(WebActionEntry(CreateShortUrlWebAction::class))
    multibind<WebActionEntry>().toInstance(WebActionEntry(ShortUrlWebAction::class))
  }
}
