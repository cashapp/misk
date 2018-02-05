package misk.testing

import misk.inject.KAbstractModule
import misk.inject.newMultibinder
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback

/** Ensures there is always a binding for extension callbacks, even if none are registered */
internal class MiskTestingModule : KAbstractModule() {
  override fun configure() {
    binder().newMultibinder<BeforeEachCallback>()
    binder().newMultibinder<AfterEachCallback>()
  }
}
