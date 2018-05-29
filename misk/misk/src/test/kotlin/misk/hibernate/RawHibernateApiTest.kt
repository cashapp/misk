package misk.hibernate

import com.google.common.util.concurrent.Service
import misk.MiskModule
import misk.config.Config
import misk.config.MiskConfig
import misk.environment.Environment
import misk.inject.KAbstractModule
import misk.inject.addMultibinderBinding
import misk.jdbc.DataSourceClustersConfig
import misk.jdbc.DataSourceConfig
import misk.jdbc.InMemoryHsqlService
import misk.resources.ResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.util.Date
import javax.inject.Inject

/** Test that we can access Hibernate's SessionFactory directly. */
@MiskTest(startService = true)
class RawHibernateApiTest {
  @MiskTestModule
  val module = object : KAbstractModule() {
    override fun configure() {
      bind(Environment::class.java).toInstance(Environment.TESTING)
      install(ResourceLoaderModule())
      install(MiskModule())

      val rootConfig = MiskConfig.load<RootConfig>("test_hibernate_app", Environment.TESTING)
      val config: DataSourceConfig = rootConfig.data_source_clusters["exemplar"]!!.writer
      binder().addMultibinderBinding<Service>().toInstance(InMemoryHsqlService(config))
      install(HibernateModule(Movies::class, config))
      install(HibernateEntityModule(Movies::class, setOf(DbMovie::class)))
    }
  }

  @Inject @Movies lateinit var sessionFactory: SessionFactory

  @Test
  fun test() {
    // Insert some movies in a transaction.
    sessionFactory.openSession().use { session ->
      val transaction = session.beginTransaction()
      session.save(DbMovie("Jurassic Park", Date()))
      session.save(DbMovie("Star Wars", Date()))
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

  data class RootConfig(val data_source_clusters: DataSourceClustersConfig) : Config
}
