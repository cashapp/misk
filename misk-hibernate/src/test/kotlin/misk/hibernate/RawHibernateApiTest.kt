package misk.hibernate

import jakarta.inject.Inject
import java.time.LocalDate
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Test that we can access Hibernate's SessionFactory directly. */
@MiskTest(startService = true)
class RawHibernateApiTest {
  @MiskExternalDependency private val dockerVitess = DockerVitess()

  @MiskTestModule val module = MoviesTestModule()

  @Inject @Movies private lateinit var sessionFactoryService: SessionFactoryService

  @Test
  fun happyPath() {
    // Insert some movies in a transaction.
    val jpId =
      sessionFactoryService.sessionFactory.openSession().use { session ->
        var transaction = session.beginTransaction()
        val jp = DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9))
        session.save(jp)
        val jg = DbActor("Jeff Goldblum")
        session.save(jg)
        session.save(DbCharacter("Ian Malcolm", jp, jg))
        transaction.commit()

        transaction = session.beginTransaction()
        session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
        transaction.commit()

        return@use jp.id
      }

    // Query those movies without a transaction.
    sessionFactoryService.sessionFactory.openSession().use { session ->
      val criteriaBuilder = session.entityManagerFactory.criteriaBuilder
      val criteria = criteriaBuilder.createQuery(DbCharacter::class.java)
      val queryRoot = criteria.from(DbCharacter::class.java)
      criteria.where(
        criteriaBuilder.equal(queryRoot.get<Id<DbMovie>>("movie_id"), jpId),
        criteriaBuilder.notEqual(queryRoot.get<String>("name"), "Leia Organa"),
      )

      val resultList = session.createQuery(criteria).resultList
      assertThat(resultList.map { it.name }).containsExactly("Ian Malcolm")
    }
  }
}
