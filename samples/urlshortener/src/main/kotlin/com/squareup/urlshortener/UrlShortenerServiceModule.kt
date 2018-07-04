package com.squareup.urlshortener

import com.google.inject.AbstractModule
import misk.config.ConfigWebModule
import misk.environment.Environment
import misk.tokens.TokenGeneratorModule
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.DefaultActionsModule

/** Binds all service dependencies including service-specific dependencies. */
class UrlShortenerServiceModule : AbstractModule() {
  override fun configure() {
    val environment = Environment.fromEnvironmentVariable()
    install(UrlShortenerModule(environment))
    install(ConfigWebModule())
    install(TokenGeneratorModule())

    install(WebModule())
    install(WebActionModule.create<CreateShortUrlWebAction>())
    install(WebActionModule.create<ShortUrlWebAction>())
    install(DefaultActionsModule())
  }
}
