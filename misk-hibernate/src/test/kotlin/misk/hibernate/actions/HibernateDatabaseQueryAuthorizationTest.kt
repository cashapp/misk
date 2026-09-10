package misk.hibernate.actions

import misk.MiskCaller
import misk.hibernate.DbMovie
import misk.hibernate.actions.HibernateDatabaseQueryWebActionModule.Companion.isAuthorizedForQuery
import misk.security.authz.AccessAnnotationEntry
import misk.web.metadata.database.DatabaseQueryMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Authorization semantics for the Database Query actions. See FRAME-2262. */
class HibernateDatabaseQueryAuthorizationTest {
  @Retention(AnnotationRetention.RUNTIME)
  @Target(AnnotationTarget.FUNCTION)
  annotation class MovieDatabaseAccess

  private fun metadataFor(vararg entries: AccessAnnotationEntry): DatabaseQueryMetadata =
    HibernateDatabaseQueryMetadataFactory(entries.toList()).fromQuery(DbMovie::class, null, MovieDatabaseAccess::class)

  private fun user(vararg capabilities: String) = MiskCaller(user = "user", capabilities = capabilities.toSet())

  private fun service(name: String) = MiskCaller(service = name)

  @Test
  fun `denies by default when the access annotation has no registered entry`() {
    // Regression for FRAME-2262: a missing AccessAnnotationEntry resolves to empty sets, which used to
    // fail open and grant read access to every @AdminDashboardAccess holder. It must now deny.
    val metadata = metadataFor()
    assertThat(metadata.allowedCapabilities).isEmpty()
    assertThat(metadata.allowedServices).isEmpty()
    assertThat(metadata.allowAnyService).isFalse()
    assertThat(metadata.allowAnyUser).isFalse()

    assertThat(isAuthorizedForQuery(user("admin_console"), metadata)).isFalse()
    assertThat(isAuthorizedForQuery(service("some-service"), metadata)).isFalse()
  }

  @Test
  fun `allows only callers holding a granted capability`() {
    val metadata = metadataFor(AccessAnnotationEntry<MovieDatabaseAccess>(capabilities = listOf("movie_reader")))
    assertThat(isAuthorizedForQuery(user("admin_console"), metadata)).isFalse()
    assertThat(isAuthorizedForQuery(user("admin_console", "movie_reader"), metadata)).isTrue()
  }

  @Test
  fun `allows only granted services`() {
    val metadata = metadataFor(AccessAnnotationEntry<MovieDatabaseAccess>(services = listOf("payments")))
    assertThat(isAuthorizedForQuery(service("fraud"), metadata)).isFalse()
    assertThat(isAuthorizedForQuery(service("payments"), metadata)).isTrue()
  }

  @Test
  fun `allowAnyUser grants any authenticated user but not services`() {
    val metadata = metadataFor(AccessAnnotationEntry<MovieDatabaseAccess>(allowAnyUser = true))
    assertThat(metadata.allowAnyUser).isTrue()
    assertThat(isAuthorizedForQuery(user(), metadata)).isTrue()
    assertThat(isAuthorizedForQuery(service("payments"), metadata)).isFalse()
  }

  @Test
  fun `allowAnyService grants any service but not users`() {
    val metadata = metadataFor(AccessAnnotationEntry<MovieDatabaseAccess>(allowAnyService = true))
    assertThat(metadata.allowAnyService).isTrue()
    assertThat(isAuthorizedForQuery(service("payments"), metadata)).isTrue()
    assertThat(isAuthorizedForQuery(user("admin_console"), metadata)).isFalse()
  }

  @Test
  fun `allowAll caller bypasses per-entity authorization`() {
    val metadata = metadataFor()
    assertThat(isAuthorizedForQuery(MiskCaller(service = "trusted", allowAll = true), metadata)).isTrue()
  }
}
