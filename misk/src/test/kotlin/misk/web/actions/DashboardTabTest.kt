package misk.web.actions

import misk.web.DashboardTab
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class DashboardTabTest {
  @Test
  internal fun tabGoodSlug() {
    DashboardTab<AdminDashboard>("good-1-slug-test", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>("bad slug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>("BadSlug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    DashboardTab<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path/", name = "Name",
      category = "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>(slug = "bad slug", url_path_prefix = "/a/path/", name = "Name",
        category = "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>(slug = "BadSlug", url_path_prefix = "/a/path/", name = "Name",
        category = "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    DashboardTab<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>(slug = "slug", url_path_prefix = "a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path", name = "Name")
    }
  }
}