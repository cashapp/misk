package misk.hibernate.actions

import jakarta.inject.Inject
import java.time.LocalDate
import kotlin.test.assertFailsWith
import misk.audit.FakeAuditClientModule
import misk.config.Config
import misk.exceptions.UnauthorizedException
import misk.hibernate.DbMovie
import misk.hibernate.HibernateEntityModule
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Transacter
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceType
import misk.security.authz.AccessAnnotationEntry
import misk.security.authz.FakeCallerAuthenticator
import misk.security.authz.MiskCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.MiskWebModule
import misk.web.WebTestingModule.Companion.TESTING_WEB_CONFIG
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Database Query actions are installable in a service that does not install `AccessControlModule`. */
@MiskTest(startService = true)
class HibernateDatabaseQueryWithoutAccessControlTest {
  @MiskTestModule val module = WithoutAccessControlTestingModule()

  @Inject
  private lateinit var executer:
    RealActionRequestExecuter<HibernateDatabaseQueryDynamicAction.Request, HibernateDatabaseQueryDynamicAction.Response>
  @Inject @Movies private lateinit var transacter: Transacter

  private val request =
    HibernateDatabaseQueryDynamicAction.Request(
      entityClass = DbMovie::class.simpleName!!,
      queryClass = "DbMovieDynamicQuery",
      query =
        HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
          select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(paths = listOf("name"))
        ),
    )

  @BeforeEach
  fun before() {
    executer.requestPath(HibernateDatabaseQueryDynamicAction.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)
    transacter.transaction { session -> session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))) }
  }

  @Test
  fun `authorized caller can query`() {
    val response = executer.executeRequest(request, user = "joey", capabilities = "dynamic_movie_query")
    assertThat(response.results).containsExactly(mapOf("name" to "Jurassic Park"))
  }

  @Test
  fun `unauthorized caller is denied`() {
    assertFailsWith<UnauthorizedException> {
      executer.executeRequest(request, user = "joey", capabilities = "admin_console")
    }
  }
}

class WithoutAccessControlTestingModule : KAbstractModule() {
  override fun configure() {
    bind<Config>().toInstance(HibernateWebActionTestingModule.TestConfig())
    install(MiskWebModule(TESTING_WEB_CONFIG))
    multibind<MiskCallerAuthenticator>().to<FakeCallerAuthenticator>()
    install(
      MoviesTestModule(
        type = DataSourceType.MYSQL,
        entitiesModule =
          object : HibernateEntityModule(Movies::class) {
            override fun configureHibernate() {
              installHibernateAdminDashboardWebActions()
              addEntityWithDynamicQuery<DbMovie, DynamicMovieQueryAccess>()
            }
          },
      )
    )
    multibind<AccessAnnotationEntry>().toInstance(HibernateDatabaseQueryTestingModule.DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY)
    install(FakeAuditClientModule())
  }
}
