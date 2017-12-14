package misk.web.actions

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ServiceManager
import misk.MiskModule
import misk.healthchecks.FakeHealthCheck
import misk.healthchecks.FakeHealthCheckModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(MiskModule::class, FakeServiceModule::class, FakeHealthCheckModule::class)
class ReadinessCheckActionTest {
    @Inject lateinit var readinessCheckAction: ReadinessCheckAction
    @Inject lateinit var serviceManager: ServiceManager
    @Inject lateinit var healthCheck: FakeHealthCheck

    @Test
    fun readinessDependsOnServiceStateAndHealthChecksPassing() {
        serviceManager.startAsync()
        serviceManager.awaitHealthy()
        assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

        healthCheck.setUnhealthy()
        assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
        healthCheck.setHealthy()
        assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(200)

        serviceManager.stopAsync()
        serviceManager.awaitStopped()
        assertThat(readinessCheckAction.readinessCheck().statusCode).isEqualTo(503)
    }
}
