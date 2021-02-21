package misk.web

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Injector
import com.google.inject.ProvisionException
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

@MiskTest(startService = false)
class InvalidActionsTest {

  @MiskTestModule
  val module = TestModule()

  @Inject private lateinit var injector: Injector

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<SomeAction>())
      install(WebActionModule.create<IdenticalAction>())
    }
  }

  @Test fun failIdenticalActions() {
    assertThrows<ProvisionException>(
      "Actions [SomeAction, IdenticalAction] have identical routing annotations."
    ) {
      injector.getInstance(ServiceManager::class.java)
    }
  }

  class SomeAction @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello() = "hello"
  }

  class IdenticalAction @Inject constructor() : WebAction {
    @Post("/hello")
    @RequestContentType("application/json")
    @ResponseContentType("application/json")
    fun hello() = "hello"
  }
}
