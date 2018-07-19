package com.squareup.urlshortener

import com.google.inject.util.Modules
import misk.environment.Environment
import misk.hibernate.HibernateTestingModule
import misk.inject.KAbstractModule
import misk.tokens.FakeTokenGeneratorModule

/** Binds all test dependencies including test-specific dependencies. */
class UrlShortenerTestModule : KAbstractModule() {
  override fun configure() {
    install(Modules.override(UrlShortenerModule(Environment.TESTING))
        .with(FakeTokenGeneratorModule()))
    install(HibernateTestingModule(UrlShortener::class))
  }
}
