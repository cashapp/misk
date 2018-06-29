package misk.web.actions

import com.google.inject.util.Modules
import misk.MiskCaller
import misk.MiskModule
import misk.security.authz.AccessControlModule
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class DefaultActionsWorkWithAccessControlModuleTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      AccessControlModule(ExampleAuthenticator::class)
  )

  @Test fun confirmCanCreateService() {
    // NB(mmihic): Nothing to do, the test is just to make sure that all
    // of the default actions can be combined with access control
  }

  class ExampleAuthenticator : MiskCallerAuthenticator {
    override fun getAuthenticatedCaller(): MiskCaller? = null
  }
}

