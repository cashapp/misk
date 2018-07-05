package com.squareup.urlshortener

import misk.environment.Environment
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.tokens.FakeTokenGeneratorModule

/** Binds all test dependencies including test-specific dependencies. */
class UrlShortenerTestModule : KAbstractModule() {
  override fun configure() {
    install(UrlShortenerModule(Environment.TESTING))
    install(FakeTokenGeneratorModule())
    install(HibernateTestingModule(UrlShortener::class))
  }
}
