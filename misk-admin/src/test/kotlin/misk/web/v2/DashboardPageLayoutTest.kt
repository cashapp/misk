package misk.web.v2

import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import misk.inject.toKey
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.metadata.MetadataTestingModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

@MiskTest
class DashboardPageLayoutTest {
  @MiskTestModule private val module = MetadataTestingModule()

  @Inject lateinit var actionScope: ActionScope
  @Inject lateinit var layout: Provider<DashboardPageLayout>

  private val fakeHttpCall = FakeHttpCall(url = "http://foobar.com/abc/123".toHttpUrl())

  @Test
  fun `happy path`() {
    actionScope.create(mapOf(HttpCall::class.toKey() to fakeHttpCall)).inScope {
      // No exception thrown on correct usage
      layout.get().newBuilder().build()
    }
  }

  @Test
  fun `no builder reuse permitted`() {
    actionScope.create(mapOf(HttpCall::class.toKey() to fakeHttpCall)).inScope {
      // Fresh builder must have newBuilder() called
      val e1 = assertFailsWith<IllegalStateException> { layout.get().build() }
      assertEquals("You must call newBuilder() before calling build() to prevent builder reuse.", e1.message)

      // No builder reuse
      val e2 =
        assertFailsWith<IllegalStateException> {
          val newBuilder = layout.get().newBuilder()
          newBuilder.build()
          // Not allowed to call build() twice on same builder
          newBuilder.build()
        }
      assertEquals("You must call newBuilder() before calling build() to prevent builder reuse.", e2.message)
    }
  }
}
