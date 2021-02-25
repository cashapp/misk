package misk.web.actions

import misk.MiskCaller
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class DefaultActionsWorkWithAccessControlModuleTest {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      multibind<MiskCallerAuthenticator>().to<ExampleAuthenticator>()
    }
  }

  @Test fun confirmCanCreateService() {
    // NB(mmihic): Nothing to do, the test is just to make sure that all
    // of the default actions can be combined with access control
  }

  class ExampleAuthenticator @Inject constructor() : MiskCallerAuthenticator {
    override fun getAuthenticatedCaller(): MiskCaller? = null
  }
}
