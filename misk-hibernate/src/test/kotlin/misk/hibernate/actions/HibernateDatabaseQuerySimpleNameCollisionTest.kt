package misk.hibernate.actions

import jakarta.inject.Inject
import java.time.Instant
import java.time.LocalDate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Table
import misk.audit.FakeAuditClientModule
import misk.hibernate.DbMovie
import misk.hibernate.DbRoot
import misk.hibernate.DbTimestampedEntity
import misk.hibernate.HibernateEntityModule
import misk.hibernate.Id
import misk.hibernate.Movies
import misk.hibernate.MoviesTestModule
import misk.hibernate.Transacter
import misk.hibernate.actions.HibernateDatabaseQueryTestingModule.Companion.DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY
import misk.hibernate.annotation.Keyspace
import misk.inject.KAbstractModule
import misk.jdbc.DataSourceType
import misk.security.authz.AccessAnnotationEntry
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the Database Query dynamic action queries the entity its authorized query was registered with, even when
 * another entity in the same transacter shares its Kotlin [simpleName][kotlin.reflect.KClass.simpleName].
 *
 * [SecretModels.DbMovie] (mapped to `actors`, registered only with `addHibernateEntity` and thus never exposed to
 * Database Query) sorts ahead of the authorized [misk.hibernate.DbMovie] (mapped to `movies`, exposed via a dynamic
 * query). A caller holding only the public movie-query capability must read `movies`, never `actors`.
 */
@MiskTest(startService = true)
class HibernateDatabaseQuerySimpleNameCollisionTest {
  @MiskTestModule val module = CollisionTestingModule()

  @Inject
  private lateinit var executer:
    RealActionRequestExecuter<HibernateDatabaseQueryDynamicAction.Request, HibernateDatabaseQueryDynamicAction.Response>
  @Inject @Movies lateinit var transacter: Transacter

  @BeforeEach
  fun before() {
    executer.requestPath(HibernateDatabaseQueryDynamicAction.HIBERNATE_QUERY_DYNAMIC_WEBACTION_PATH)

    // PUBLIC_CONTROL lives in `movies` (authorized). SECRET_PROOF lives in `actors` (never exposed).
    transacter.allowCowrites().transaction { session ->
      session.save(DbMovie("PUBLIC_CONTROL"))
      session.save(SecretModels.DbMovie("SECRET_PROOF"))
    }
  }

  @Test
  fun `colliding simple name resolves to the authorized entity, not the first-registered one`() {
    // Precondition: both entities share the simple name `DbMovie`, and the protected one sorts first.
    val collidingOrder = transacter.entities().filter { it.simpleName == "DbMovie" }.map { it.qualifiedName }
    assertThat(collidingOrder).hasSize(2)
    assertThat(collidingOrder.first()).isEqualTo(SecretModels.DbMovie::class.qualifiedName)

    val response =
      executer.executeRequest(
        HibernateDatabaseQueryDynamicAction.Request(
          entityClass = DbMovie::class.simpleName!!,
          queryClass = "DbMovieDynamicQuery",
          query =
            HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuery(
              select = HibernateDatabaseQueryMetadataFactory.Companion.DynamicQuerySelect(paths = listOf("name"))
            ),
        ),
        user = "low-user",
        // Authorized only for the public movie query; no capability for the `actors`/SecretModels entity.
        capabilities = DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY.capabilities.joinToString() + ",admin_console",
      )

    val names = response.results.map { (it as Map<*, *>)["name"] }
    assertThat(names).containsExactly("PUBLIC_CONTROL")
    assertThat(names).doesNotContain("SECRET_PROOF")
  }
}

/**
 * A second entity whose Kotlin simple name collides with [misk.hibernate.DbMovie] but which maps to a different table
 * (`actors`). A distinct JPA `@Entity` name keeps Hibernate happy; the collision is on the Kotlin
 * [simpleName][kotlin.reflect.KClass.simpleName].
 */
object SecretModels {
  @Entity(name = "SecretDbMovie")
  @Table(name = "actors")
  @Keyspace("movies_sharded")
  class DbMovie() : DbRoot<DbMovie>, DbTimestampedEntity {
    @javax.persistence.Id @GeneratedValue(strategy = GenerationType.IDENTITY) override lateinit var id: Id<DbMovie>

    @Column override lateinit var updated_at: Instant

    @Column override lateinit var created_at: Instant

    @Column(nullable = false) lateinit var name: String

    @Column var birth_date: LocalDate? = null

    constructor(name: String) : this() {
      this.name = name
    }
  }
}

class CollisionTestingModule : KAbstractModule() {
  override fun configure() {
    install(HibernateWebActionTestingModule())
    install(
      MoviesTestModule(
        type = DataSourceType.MYSQL,
        entitiesModule =
          object : HibernateEntityModule(Movies::class) {
            override fun configureHibernate() {
              installHibernateAdminDashboardWebActions()

              // Registered FIRST and WITHOUT a dynamic query, so it is never exposed to Database Query yet
              // sorts ahead of the authorized entity in transacter.entities().
              addHibernateEntity(SecretModels.DbMovie::class)
              // The authorized, Database-Query-exposed entity, mapped to `movies`.
              addEntityWithDynamicQuery<DbMovie, DynamicMovieQueryAccess>()
            }
          },
      )
    )

    multibind<AccessAnnotationEntry>().toInstance(DYNAMIC_MOVIE_QUERY_ACCESS_ENTRY)

    install(FakeAuditClientModule())
  }
}
