package com.squareup.urlshortener

import misk.environment.Environment
import misk.inject.KAbstractModule

/** Binds all test dependencies including test-specific dependencies. */
class UrlShortenerTestModule : KAbstractModule() {
  override fun configure() {
    install(UrlShortenerModule(Environment.TESTING))
  }
}
