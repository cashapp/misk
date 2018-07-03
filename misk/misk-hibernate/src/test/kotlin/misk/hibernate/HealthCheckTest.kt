package misk.hibernate

import misk.healthchecks.HealthCheck
import misk.mockito.Mockito.mock
import misk.mockito.Mockito.whenever
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.HibernateException
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject

@MiskTest(startService = true)
class HealthCheckTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject lateinit var fakeClock: FakeClock
  @Inject lateinit var healthChecks: List<HealthCheck>
  @Inject @Movies lateinit var config: DataSourceConfig
  @Inject @Movies lateinit var sessionFactory: SessionFactory

  @Test
  fun healthy() {
    fakeClock.setNow(Instant.now())

    val status = HibernateHealthCheck(sessionFactory, config, fakeClock).status()
    assertThat(status.isHealthy).isTrue()
  }

  @Test
  fun databaseConnectivityFailure() {
    val mockSessionFactory: SessionFactory = mock()
    whenever(mockSessionFactory.openSession()).thenThrow(HibernateException("Cannot open session"))

    val status = HibernateHealthCheck(mockSessionFactory, config, fakeClock).status()
    assertThat(status.isHealthy).isFalse()
    assertThat(status.messages).contains("Hibernate: failed to query movies database")
  }

  @Test
  fun clockSkew() {
    val skew = HibernateHealthCheck.CLOCK_SKEW_UNHEALTHY_THRESHOLD.multipliedBy(2)
    fakeClock.setNow(Instant.now().minus(skew))

    val status = HibernateHealthCheck(sessionFactory, config, fakeClock).status()
    assertThat(status.isHealthy).isFalse()
    assertThat(status.messages).anyMatch {
      it.startsWith("Hibernate: host and movies database clocks have drifted")
    }
  }

  @Test
  fun isInjected() {
    assertThat(healthChecks).anyMatch { it is HibernateHealthCheck }
  }
}
