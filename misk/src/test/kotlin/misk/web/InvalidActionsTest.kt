package misk.web

import com.google.common.util.concurrent.ServiceManager
import com.google.inject.Guice
import com.google.inject.ProvisionException
import misk.web.actions.WebAction
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.inject.Inject

class InvalidActionsTest {

  @Test fun failIdenticalActions() {
    assertThrows<ProvisionException>(
      "Actions [SomeAction, IdenticalAction] have identical routing annotations."
    ) {
      val injector = Guice.createInjector(
        WebTestingModule(),
        WebActionModule.create<SomeAction>(),
        WebActionModule.create<IdenticalAction>()
      )
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