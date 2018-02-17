package misk.web.actions

import misk.testing.MiskTest
import misk.web.Response
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class NotFoundActionTest {
  @Inject lateinit var notFoundAction: NotFoundAction

  @Test
  fun test() {
    assertThat(notFoundAction.notFound("notfound")).isEqualTo(
        Response("Nothing found at /notfound", statusCode = 404)
    )
  }
}
