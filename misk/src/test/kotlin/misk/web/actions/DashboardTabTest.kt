package misk.web.actions

import misk.web.DashboardTab
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class DashboardTabTest {
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
    DashboardTab(slug = "slug", url_path_prefix = "/a/path/", name = "Name",
      category = "@tea-pot_418")
  }

  @Test
  internal fun tabCategoryWithSpace() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "bad slug", url_path_prefix = "/a/path/", name = "Name",
        category = "bad icon")
    }
  }

  @Test
  internal fun tabCategoryWithUpperCase() {
    assertFailsWith<IllegalArgumentException> {
      DashboardTab(slug = "BadSlug", url_path_prefix = "/a/path/", name = "Name",
        category = "Bad-Icon")
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