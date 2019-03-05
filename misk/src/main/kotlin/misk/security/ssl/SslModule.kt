package misk.security.ssl

import misk.inject.KAbstractModule

/**
 * SslModule binds utilities for creating SSL connections.
 */
class SslModule : KAbstractModule() {
  override fun configure() {
    bind<SslLoader>()
    bind<SslContextFactory>()
  }
}