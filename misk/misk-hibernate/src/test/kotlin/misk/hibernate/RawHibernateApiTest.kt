package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

/** Test that we can access Hibernate's SessionFactory directly. */
@MiskTest(startService = true)
class RawHibernateApiTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var sessionFactory: SessionFactory

  @Test
  fun test() {
    // Insert some movies in a transaction.
    sessionFactory.openSession().use { session ->
      val transaction = session.beginTransaction()
      session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
      transaction.commit()
    }

    // Query those movies without a transaction.
    sessionFactory.openSession().use { session ->
      val criteriaBuilder = session.entityManagerFactory.criteriaBuilder
      val criteria = criteriaBuilder.createQuery(DbMovie::class.java)
      val queryRoot = criteria.from(DbMovie::class.java)
      criteria.where(criteriaBuilder.notEqual(queryRoot.get<String>("name"), "Star Wars"))
      val resultList: List<DbMovie> = session.createQuery(criteria).resultList
      assertThat(resultList.map { it.name }).containsExactly("Jurassic Park")
    }
  }
}
