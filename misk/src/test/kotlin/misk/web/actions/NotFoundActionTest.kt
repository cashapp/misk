package misk.web.actions

import com.google.common.truth.Truth.assertThat
import misk.testing.MiskTestRule
import misk.web.Response
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

class NotFoundActionTest {
    @Rule
    @JvmField
    val testRule = MiskTestRule()

    @Inject lateinit var notFoundAction: NotFoundAction

    @Test
    fun test() {
        assertThat(notFoundAction.notFound("notfound")).isEqualTo(Response("Nothing found at /notfound", statusCode = 404))
    }
}
