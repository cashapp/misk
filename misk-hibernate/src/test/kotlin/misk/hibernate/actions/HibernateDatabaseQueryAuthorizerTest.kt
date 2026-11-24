package misk.hibernate.actions

import misk.MiskCaller
import misk.security.authz.AccessAnnotationEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HibernateDatabaseQueryAuthorizerTest {
  private val authorizer = HibernateDatabaseQueryAuthorizer(excludeFromAllowAnyService = listOf("web-proxy"))

  private fun user(vararg capabilities: String) = MiskCaller(user = "user", capabilities = capabilities.toSet())

  private fun service(name: String) = MiskCaller(service = name)

  @Test
  fun `denies when no entry is registered`() {
    assertThat(authorizer.isAuthorized(user("admin_console"), null)).isFalse()
    assertThat(authorizer.isAuthorized(service("payments"), null)).isFalse()
  }

  @Test
  fun `denies when the entry grants nothing`() {
    val entry = AccessAnnotationEntry<MovieDatabaseAccess>()
    assertThat(authorizer.isAuthorized(user("admin_console"), entry)).isFalse()
    assertThat(authorizer.isAuthorized(service("payments"), entry)).isFalse()
  }

  @Test
  fun `allows granted capabilities and services only`() {
    val entry =
      AccessAnnotationEntry<MovieDatabaseAccess>(services = listOf("payments"), capabilities = listOf("movies"))
    assertThat(authorizer.isAuthorized(user("admin_console"), entry)).isFalse()
    assertThat(authorizer.isAuthorized(user("admin_console", "movies"), entry)).isTrue()
    assertThat(authorizer.isAuthorized(service("fraud"), entry)).isFalse()
    assertThat(authorizer.isAuthorized(service("payments"), entry)).isTrue()
  }

  @Test
  fun `allowAnyUser allows users but not services`() {
    val entry = AccessAnnotationEntry<MovieDatabaseAccess>(allowAnyUser = true)
    assertThat(authorizer.isAuthorized(user(), entry)).isTrue()
    assertThat(authorizer.isAuthorized(service("payments"), entry)).isFalse()
  }

  @Test
  fun `allowAnyService allows non-excluded services but not users`() {
    val entry = AccessAnnotationEntry<MovieDatabaseAccess>(allowAnyService = true)
    assertThat(authorizer.isAuthorized(service("payments"), entry)).isTrue()
    assertThat(authorizer.isAuthorized(service("web-proxy"), entry)).isFalse()
    assertThat(authorizer.isAuthorized(user("admin_console"), entry)).isFalse()
  }

  @Test
  fun `allowAll bypasses the entry`() {
    assertThat(authorizer.isAuthorized(MiskCaller(service = "trusted", allowAll = true), null)).isTrue()
  }

  annotation class MovieDatabaseAccess
}
