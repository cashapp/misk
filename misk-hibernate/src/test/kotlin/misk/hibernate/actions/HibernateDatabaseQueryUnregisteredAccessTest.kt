package misk.hibernate.actions

import jakarta.inject.Inject
import jakarta.inject.Qualifier
import kotlin.test.assertFailsWith
import misk.audit.FakeAuditClientModule
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbMovie
import misk.hibernate.HibernateEntityModule
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceType
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

/** An entity whose access annotation has no [misk.security.authz.AccessAnnotationEntry] is not queryable. */
@MiskTest(startService = true)
class HibernateDatabaseQueryUnregisteredAccessTest {
  @MiskTestModule val module = UnregisteredAccessTestingModule()

  @Inject
  private lateinit var executer:
    RealActionRequestExecuter<HibernateDatabaseQueryDynamicAction.Request, HibernateDatabaseQueryDynamicAction.Response>

  @Test
  fun `admin dashboard user is denied`() {
    executer.requestPath(HibernateDatabaseQueryDynamicAction.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
    assertFailsWith<UnauthorizedException> {
      executer.executeRequest(
        HibernateDatabaseQueryDynamicAction.Request(
          entityClass = DbMovie::class.simpleName!!,
          queryClass = "DbMovieDynamicQuery",
          query = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(),
        ),
        user = "joey",
        capabilities = "admin_console",
      )
    }
  }
}

class UnregisteredAccessTestingModule : KAbstractModule() {
  override fun configure() {
    install(HibernateWebActionTestingModule())
    install(
      MoviesTestModule(
        type = DataSourceType.MYSQL,
        entitiesModule =
          object : HibernateEntityModule(Movies::class) {
            override fun configureHibernate() {
              installHibernateAdminDashboardWebActions()
              addEntityWithDynamicQuery<DbMovie, UnregisteredMovieQueryAccess>()
            }
          },
      )
    )
    install(FakeAuditClientModule())
  }
}

@Qualifier @Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION) annotation class UnregisteredMovieQueryAccess
