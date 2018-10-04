package misk.web.actions

import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
internal class AdminDashboardTabTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var adminDashboardTabs: List<AdminDashboardTab>

  private val logger = getLogger<AdminDashboardTabTest>()

  class TestModule : KAbstractModule() {
    override fun configure() {
      multibind<AdminDashboardTab>().toInstance(AdminDashboardTab(
          slug = "dashboard",
          url_path_prefix = "/_admin/dashboard/",
          name = "Dashboard"
      ))
    }
  }

  @Test
  internal fun testBindings() {
    logger.info(adminDashboardTabs.toString())
  }

  @Test
  internal fun tabGoodSlug() {
    AdminDashboardTab("good-1-slug-test", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("bad slug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab("BadSlug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    AdminDashboardTab(slug = "slug", url_path_prefix = "/a/path/", name = "Name", category = "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab(slug = "bad slug", url_path_prefix = "/a/path/", name = "Name", category = "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab(slug = "BadSlug", url_path_prefix = "/a/path/", name = "Name", category = "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    AdminDashboardTab(slug = "slug", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab(slug = "slug", url_path_prefix = "a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      AdminDashboardTab(slug = "slug", url_path_prefix = "/a/path", name = "Name")
    }
  }
}