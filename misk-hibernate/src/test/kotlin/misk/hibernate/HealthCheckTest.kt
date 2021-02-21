package misk.hibernate

import misk.healthchecks.HealthCheck
import misk.jdbc.DataSourceConfig
import misk.mockito.Mockito.mock
import misk.mockito.Mockito.whenever
import misk.services.FakeService
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.HibernateException
import org.hibernate.SessionFactory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import javax.inject.Inject
import javax.inject.Provider

@MiskTest(startService = true)
class HealthCheckTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject lateinit var fakeClock: FakeClock
  @Inject lateinit var serviceProvider: Provider<FakeService>
  @Inject lateinit var healthChecks: List<HealthCheck>
  @Inject @Movies lateinit var config: DataSourceConfig
  @Inject @Movies lateinit var sessionFactory: Provider<SessionFactory>

  @BeforeEach fun setUp() {
    serviceProvider.get().startAsync()
    serviceProvider.get().awaitRunning()
  }

  @Test
  fun healthy() {
    fakeClock.setNow(Instant.now())

    val status = HibernateHealthCheck(
      Movies::class, serviceProvider, sessionFactory, fakeClock
    ).status()
    assertThat(status.isHealthy).isTrue
  }

  @Test
  fun databaseConnectivityFailure() {
    val mockSessionFactory: SessionFactory = mock()
    whenever(mockSessionFactory.openSession()).thenThrow(HibernateException("Cannot open session"))

    val status = HibernateHealthCheck(
      Movies::class, serviceProvider, Provider { mockSessionFactory }, fakeClock
    ).status()
    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).contains("Hibernate: failed to query Movies database")
  }

  @Test
  fun clockSkew() {
    val skew = HibernateHealthCheck.CLOCK_SKEW_UNHEALTHY_THRESHOLD.multipliedBy(2)
    fakeClock.setNow(Instant.now().minus(skew))

    val status = HibernateHealthCheck(
      Movies::class, serviceProvider, sessionFactory, fakeClock
    ).status()
    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).anyMatch {
      it.startsWith("Hibernate: host and Movies database clocks have drifted")
    }
  }

  @Test
  fun serviceIsNotRunning() {
    serviceProvider.get().stopAsync()
    serviceProvider.get().awaitTerminated()

    val status = HibernateHealthCheck(
      Movies::class, serviceProvider, sessionFactory, fakeClock
    ).status()

    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).contains("Hibernate: Movies database service is TERMINATED")
  }

  @Test
  fun isInjected() {
    assertThat(healthChecks).anyMatch { it is HibernateHealthCheck }
  }
}
