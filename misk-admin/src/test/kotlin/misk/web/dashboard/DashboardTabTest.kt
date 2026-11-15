package misk.web.dashboard

import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

internal class DashboardTabTest {
  @Test
  internal fun tabGoodSlug() {
    DashboardTab("good-1-slug-test", url_path_prefix = "/a/path/", menuLabel = "Name", dashboard_slug = "admin")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab("bad slug", url_path_prefix = "/a/path/", menuLabel = "Name", dashboard_slug = "admin")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab("BadSlug", url_path_prefix = "/a/path/", menuLabel = "Name", dashboard_slug = "admin")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    DashboardTab(
      slug = "slug",
      url_path_prefix = "/a/path/",
      menuLabel = "Name",
      menuCategory = "@tea-pot_418",
      dashboard_slug = "admin",
    )
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(
        slug = "bad slug",
        url_path_prefix = "/a/path/",
        menuLabel = "Name",
        menuCategory = "bad icon",
        dashboard_slug = "admin",
      )
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(
        slug = "BadSlug",
        url_path_prefix = "/a/path/",
        menuLabel = "Name",
        menuCategory = "Bad-Icon",
        dashboard_slug = "admin",
      )
    }
  }

  @Test
  internal fun tabGoodPath() {
    DashboardTab(slug = "slug", url_path_prefix = "/a/path/", menuLabel = "Name", dashboard_slug = "admin")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "slug", url_path_prefix = "a/path/", menuLabel = "Name", dashboard_slug = "admin")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "slug", url_path_prefix = "/a/path", menuLabel = "Name", dashboard_slug = "admin")
    }
  }
}
