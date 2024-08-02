package misk.web.metadata.guice

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GitHubSourceUrlProviderTest {
  @Test
  fun testUrlForSource() {
    verifyUrl(
      "com.squareup.skim.dashboard.SkimDashboardModule.configure(SkimDashboardModule.kt:23)",
      "https://github.com/search?q=%22package+com.squareup.skim.dashboard%22+SkimDashboardModule+configure&type=code"
    )
  }

  @Test
  fun testUrlForSourceShortPackage() {
    verifyUrl(
      "package.Class.function(Class.kt:23)",
      "https://github.com/search?q=%22package+package%22+Class+function&type=code"
    )
  }

  @Test
  fun testUrlForSourceInnerClass() {
    verifyUrl(
      "package.Class\$InnerClass.function(Class.kt:23)",
      "https://github.com/search?q=%22package+package%22+Class+InnerClass+function&type=code"
    )
  }

  private fun verifyUrl(source: String, expectedUrl: String) {
    val provider = GitHubSourceUrlProvider()
    val url = provider.urlForSource(source)
    assertEquals(expectedUrl, url)
  }
}
