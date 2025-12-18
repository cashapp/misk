package misk.hibernate

import com.google.common.util.concurrent.Service
import com.google.inject.Provider
import jakarta.inject.Inject
import java.time.Instant
import misk.healthchecks.HealthCheck
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.vitess.testing.utilities.DockerVitess
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.HibernateException
import org.hibernate.SessionFactory
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@MiskTest(startService = true)
class HealthCheckTest {
  @MiskExternalDependency private val dockerVitess = DockerVitess()

  @MiskTestModule val module = MoviesTestModule()

  @Inject @Movies private lateinit var sessionFactory: Provider<SessionFactoryService>
  @Inject private lateinit var fakeClock: FakeClock
  @Inject private lateinit var healthChecks: List<HealthCheck>

  @Test
  fun healthy() {
    fakeClock.setNow(Instant.now())

    val status = HibernateHealthCheck(Movies::class, sessionFactory, fakeClock).status()
    assertThat(status.isHealthy).isTrue
  }

  @Test
  fun databaseConnectivityFailure() {
    val mockSessionFactoryService: SessionFactoryService = mock()
    val mockSessionFactory: SessionFactory = mock()
    whenever(mockSessionFactoryService.state()).thenReturn(Service.State.RUNNING)
    whenever(mockSessionFactoryService.sessionFactory).thenReturn(mockSessionFactory)
    whenever(mockSessionFactory.openSession()).thenThrow(HibernateException("Cannot open session"))

    val status = HibernateHealthCheck(Movies::class, { mockSessionFactoryService }, fakeClock).status()
    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).contains("Hibernate: failed to query Movies database")
  }

  @Test
  fun clockSkew() {
    val skew = HibernateHealthCheck.CLOCK_SKEW_UNHEALTHY_THRESHOLD.multipliedBy(2)
    fakeClock.setNow(Instant.now().minus(skew))

    val status = HibernateHealthCheck(Movies::class, sessionFactory, fakeClock).status()
    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).anyMatch { it.startsWith("Hibernate: host and Movies database clocks have drifted") }
  }

  @Test
  fun serviceIsNotRunning() {
    sessionFactory.get().stopAsync()
    sessionFactory.get().awaitTerminated()

    val status = HibernateHealthCheck(Movies::class, sessionFactory, fakeClock).status()

    assertThat(status.isHealthy).isFalse
    assertThat(status.messages).contains("Hibernate: Movies database service is TERMINATED")
  }

  @Test
  fun isInjected() {
    assertThat(healthChecks).anyMatch { it is HibernateHealthCheck }
  }
}
