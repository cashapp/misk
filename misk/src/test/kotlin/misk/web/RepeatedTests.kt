package misk.web

import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.actions.WebAction
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import javax.inject.Inject

@MiskTest(startService = true)
class RepeatedTests {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<Hello>())
    }
  }

  class Hello @Inject constructor() : WebAction {
    @Get("/hello") fun hello() = "Hello"
  }

  @Inject lateinit var webTestClient: WebTestClient

  companion object {
    @JvmStatic
    fun `runs jetty`(): Stream<Arguments> = IntRange(0, 500)
      .map { Arguments.of(it) }
      .stream()
  }

  @ParameterizedTest
  @MethodSource
  fun `runs jetty`(x: Int) {
    webTestClient.get("/hello")
    // nothing
  }
}
