package misk.feature.testing

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import misk.inject.KAbstractModule

/**
 * Binds a [Moshi] instance for testing.
 *
 * Misk services automatically get this binding, but no need to depend on all of misk to test this.
 */
class MoshiTestingModule : KAbstractModule() {
  override fun configure() {
    bind<Moshi>().toInstance(
      Moshi.Builder()
        .add(KotlinJsonAdapterFactory()) // Added last for lowest precedence.
        .build()
    )
  }
}
