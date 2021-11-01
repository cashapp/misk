package misk.feature.testing

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import wisp.moshi.defaultKotlinMoshi

/**
 * Binds a [Moshi] instance for testing.
 *
 * Misk services automatically get this binding, but no need to depend on all of misk to test this.
 */
internal class MoshiTestingModule : KAbstractModule() {
  override fun configure() {
    bind<Moshi>().toInstance(defaultKotlinMoshi)
  }
}
