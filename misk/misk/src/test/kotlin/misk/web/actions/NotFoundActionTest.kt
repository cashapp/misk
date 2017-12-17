package misk.web.actions

import com.google.common.truth.Truth.assertThat
import misk.testing.ActionTest
import misk.web.Response
import org.junit.jupiter.api.Test
import javax.inject.Inject

@ActionTest
class NotFoundActionTest {
  @Inject lateinit var notFoundAction: NotFoundAction

  @Test
  fun test() {
    assertThat(notFoundAction.notFound("notfound")).isEqualTo(Response("Nothing found at /notfound", statusCode = 404))
  }
}
