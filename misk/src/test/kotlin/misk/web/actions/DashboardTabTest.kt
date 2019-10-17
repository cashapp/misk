package misk.web.actions

import misk.web.DashboardTabProvider
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class DashboardTabTest {
  @Test
  internal fun tabGoodSlug() {
    DashboardTabProvider<AdminDashboard>("good-1-slug-test", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabSlugWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>("bad slug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabSlugWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>("BadSlug", url_path_prefix = "/a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabGoodCategory() {
    DashboardTabProvider<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path/", name = "Name",
      category = "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>(slug = "bad slug", url_path_prefix = "/a/path/", name = "Name",
        category = "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>(slug = "BadSlug", url_path_prefix = "/a/path/", name = "Name",
        category = "Bad-Icon")
    }
  }

  @Test
  internal fun tabGoodPath() {
    DashboardTabProvider<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path/", name = "Name")
  }

  @Test
  internal fun tabPathWithoutLeadingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>(slug = "slug", url_path_prefix = "a/path/", name = "Name")
    }
  }

  @Test
  internal fun tabPathWithoutTrailingSlash() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTabProvider<AdminDashboard>(slug = "slug", url_path_prefix = "/a/path", name = "Name")
    }
  }
}