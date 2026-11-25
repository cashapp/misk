package misk.hibernate.actions

import jakarta.inject.Inject
import misk.MiskCaller
import misk.inject.KAbstractModule
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.ExcludeFromAllowAnyService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Services excluded from `allowAnyService` alongside `AccessControlModule` reach the Database Query authorizer. */
@MiskTest
class HibernateDatabaseQueryServiceExclusionTest {
  @MiskTestModule val module = ServiceExclusionTestingModule()

  @Inject private lateinit var authorizer: HibernateDatabaseQueryAuthorizer

  @Test
  fun `excluded service is not authorized by allowAnyService`() {
    val entry = AccessAnnotationEntry<DynamicMovieQueryAccess>(allowAnyService = true)

    assertThat(authorizer.isAuthorized(MiskCaller(service = "web-proxy"), entry)).isFalse()
    assertThat(authorizer.isAuthorized(MiskCaller(service = "movie-service"), entry)).isTrue()
  }
}

class ServiceExclusionTestingModule : KAbstractModule() {
  override fun configure() {
    install(HibernateDatabaseQueryTestingModule())
    multibind<String, ExcludeFromAllowAnyService>().toInstance("web-proxy")
  }
}
