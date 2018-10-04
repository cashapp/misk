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

  @Inject lateinit var adminTabAction: AdminDashboardTabAction
  @Inject lateinit var adminDashboardTabs: List<AdminDashboardTab>

  private val logger = getLogger<AdminTabActionTest>()

  class TestModule : KAbstractModule() {
    override fun configure() {
      multibind<AdminDashboardTab>().toInstance(
          AdminDashboardTab(
              "Dashboard",
              "dashboard",
              "/_admin/dashboard/"
          ))
    }
  }

  @Test
  internal fun testBindings() {
    logger.info(adminDashboardTabs.toString())
  }

  @Test
  internal fun tabGoodSlug() {
    AdminDashboardTab("Name", "@good-slug_test", "/a/path/")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "bad slug", "/a/path/")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "BadSlug", "/a/path/")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    AdminDashboardTab("Name", "slug", "/a/path/", "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "bad slug", "/a/path/", "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "BadSlug", "/a/path/", "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    AdminDashboardTab("Name", "slug", "/a/path/")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "slug", "a/path/")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("Name", "slug", "/a/path")
    }
  }

//  @Test
//  internal fun getInstalledTabs() {
//
//    lateinit var registeredDashboardTabs: List<AdminDashboardTab>
//
//    val tabRequest = adminTabAction.getAll()
//  }
}