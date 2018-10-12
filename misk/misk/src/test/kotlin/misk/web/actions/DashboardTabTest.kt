package misk.web.actions

import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.DashboardTab
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
internal class DashboardTabTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var dashboardTabs: List<DashboardTab>

  private val logger = getLogger<DashboardTabTest>()

  class TestModule : KAbstractModule() {
    override fun configure() {
      multibind<DashboardTab>().toInstance(DashboardTab(
          slug = "dashboard",
          url_path_prefix = "/_admin/dashboard/",
          name = "Dashboard"
      ))
    }
  }

  @Test
  internal fun testBindings() {
    logger.info(dashboardTabs.toString())
  }

  @Test
  internal fun tabGoodSlug() {
    DashboardTab("good-1-slug-test", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab("bad slug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab("BadSlug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    DashboardTab(slug = "slug", url_path_prefix = "/a/path/", name = "Name", category = "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "bad slug", url_path_prefix = "/a/path/", name = "Name", category = "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "BadSlug", url_path_prefix = "/a/path/", name = "Name", category = "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    DashboardTab(slug = "slug", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "slug", url_path_prefix = "a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "slug", url_path_prefix = "/a/path", name = "Name")
    }
  }
}