package com.squareup.urlshortener

import com.google.inject.name.Names
import misk.MiskServiceModule
import misk.client.HttpClientModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule

/** Binds dependencies for all environments. */
class UrlShortenerModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    val config = MiskConfig.load<UrlShortenerConfig>("urlshortener", environment)
    install(ConfigModule.create("urlshortener", config))

    install(MiskServiceModule())
    install(EnvironmentModule(environment))
    install(HttpClientModule("for_shortened_urls", Names.named("for_shortened_urls")))

    bind<UrlStore>().to<RealUrlStore>()

    install(HibernateModule(UrlShortener::class, config.data_source_cluster.writer))
    install(object : HibernateEntityModule(UrlShortener::class) {
      override fun configureHibernate() {
        addEntities(DbShortenedUrl::class)
      }
    })
  }
}
