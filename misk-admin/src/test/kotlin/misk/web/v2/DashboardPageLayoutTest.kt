package misk.web.v2

import com.google.inject.Provider
import jakarta.inject.Inject
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import misk.inject.toKey
import misk.scope.ActionScope
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.FakeHttpCall
import misk.web.HttpCall
import misk.web.metadata.MetadataTestingModule
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Nested
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

  @Test
  fun `turbo enabled by default includes turbo scripts`() {
    actionScope.create(mapOf(HttpCall::class.toKey() to fakeHttpCall)).inScope {
      val html = layout.get().newBuilder().build()
      assertContains(html, "turbo/7.2.5/es2017-umd.min.js")
      assertContains(html, "turbo-root")
    }
  }

  @Test
  fun `turbo disabled excludes turbo scripts`() {
    actionScope.create(mapOf(HttpCall::class.toKey() to fakeHttpCall)).inScope {
      val html = layout.get().newBuilder().enableTurbo(false).build()
      assertFalse(html.contains("turbo/7.2.5/es2017-umd.min.js"))
      assertFalse(html.contains("turbo-root"))
    }
  }
}

@MiskTest
class DashboardPageLayoutTurboDisabledTest {
  @MiskTestModule private val module = MetadataTestingModule(enableTurbo = false)

  @Inject lateinit var actionScope: ActionScope
  @Inject lateinit var layout: Provider<DashboardPageLayout>

  private val fakeHttpCall = FakeHttpCall(url = "http://foobar.com/abc/123".toHttpUrl())

  @Test
  fun `turbo disabled via module config excludes turbo scripts`() {
    actionScope.create(mapOf(HttpCall::class.toKey() to fakeHttpCall)).inScope {
      val html = layout.get().newBuilder().build()
      assertFalse(html.contains("turbo/7.2.5/es2017-umd.min.js"))
      assertFalse(html.contains("turbo-root"))
    }
  }
}
