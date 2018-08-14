package misk.web.actions

import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
internal class AdminTabActionTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var adminTabAction: AdminTabAction
  @Inject lateinit var adminTabs: List<AdminTab>

  private val logger = getLogger<AdminTabActionTest>()

  class TestModule : KAbstractModule() {
    override fun configure() {
      multibind<AdminTab>().toInstance(AdminTab(
          "Dashboard",
          "dashboard",
          "/_admin/dashboard"
      ))
    }
  }

  @Test
  internal fun testBindings() {
    logger.info(adminTabs.toString())
  }

  @Test
  internal fun tabGoodSlug() {
    AdminTab("Name", "@good-slug_test", "/a/path")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminTab("Name", "bad slug", "/a/path")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminTab("Name", "BadSlug", "/a/path")
    }
  }

  @Test
  internal fun tabGoodIcon() {
    AdminTab("Name", "slug", "/a/path", "@tea-pot_418")
  }

  @Test
  internal fun tabIconWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminTab("Name", "bad slug", "/a/path", "bad icon")
    }
  }

  @Test
  internal fun tabIconWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminTab("Name", "BadSlug", "/a/path", "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    AdminTab("Name", "slug", "/a/path")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      AdminTab("Name", "slug", "a/path")
    }
  }

//  @Test
//  internal fun getInstalledTabs() {
//
//    lateinit var registeredTabs: List<AdminTab>
//
//    val tabRequest = adminTabAction.getAll()
//  }
}