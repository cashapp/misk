package misk.web.v2

import misk.inject.toKey
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.actions.WebAction
import misk.web.metadata.MetadataTestingModule
import org.junit.jupiter.api.Test
import com.google.inject.Inject
import com.google.inject.Provider
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@MiskTest
class DashboardPageLayoutTest {
  @MiskTestModule
  private val module = MetadataTestingModule()

  @Inject lateinit var actionScope: ActionScope
  @Inject lateinit var layout: Provider<DashboardPageLayout>

  @Test
  fun `happy path`() {
    actionScope.enter(mapOf(HttpCall::class.toKey() to FakeHttpCall())).use {
      // No exception thrown on correct usage
      layout.get().newBuilder().path("/abc/123").build()
    }
  }

  @Test
  fun `no builder reuse permitted`() {
    actionScope.enter(mapOf(HttpCall::class.toKey() to FakeHttpCall())).use {
      // Fresh builder must have newBuilder() called
      val e1 = assertFailsWith<IllegalStateException> { layout.get().build() }
      assertEquals(
        "You must call newBuilder() before calling build() to prevent builder reuse.", e1.message
      )

      // No builder reuse
      val e2 = assertFailsWith<IllegalStateException> {
        val newBuilder = layout.get().newBuilder()
        newBuilder.path("/abc/123").build()
        // Not allowed to call build() twice on same builder
        newBuilder.path("/abc/123").build()
      }
      assertEquals(
        "You must call newBuilder() before calling build() to prevent builder reuse.", e2.message
      )
    }
  }
}
