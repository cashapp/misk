package com.squareup.urlshortener

import misk.MiskModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.hibernate.HibernateEntityModule
import misk.hibernate.HibernateModule
import misk.inject.KAbstractModule
import misk.resources.ResourceLoaderModule

/** Binds dependencies for all environments. */
class UrlShortenerModule(val environment: Environment) : KAbstractModule() {
  override fun configure() {
    val config = MiskConfig.load<UrlShortenerConfig>("urlshortener", environment)
    install(ConfigModule.create("urlshortener", config))

    install(MiskModule())
    install(ResourceLoaderModule())
    install(EnvironmentModule(environment))

    bind(UrlStore::class.java).to(RealUrlStore::class.java)

    install(HibernateModule(UrlShortener::class, config.data_source_cluster.writer))
    install(object : HibernateEntityModule(UrlShortener::class) {
      override fun configureHibernate() {
        addEntities(DbShortenedUrl::class)
      }
    })
  }
}
