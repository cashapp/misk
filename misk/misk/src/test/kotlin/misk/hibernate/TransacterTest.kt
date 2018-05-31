package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Date
import javax.inject.Inject

@MiskTest(startService = true)
class TransacterTest {
  @MiskTestModule
  val module = HibernateTestModule()

  @Inject @Movies lateinit var transacter: Transacter

  @Test
  fun test() {
    // Insert some movies in a transaction.
    transacter.transaction { session ->
      session.save(DbMovie("Jurassic Park", Date()))
      session.save(DbMovie("Star Wars", Date()))
    }

    // Query some movies.
    transacter.transaction { session ->
      val criteriaBuilder = session.newCriteriaBuilder()
      val criteria = criteriaBuilder.createQuery(DbMovie::class.java)
      val queryRoot = criteria.from(DbMovie::class.java)
      criteria.where(criteriaBuilder.notEqual(queryRoot.get<String>("name"), "Star Wars"))
      val resultList: List<DbMovie> = session.query(criteria).list()
      assertThat(resultList.map { it.name }).containsExactly("Jurassic Park")
    }
  }
}
