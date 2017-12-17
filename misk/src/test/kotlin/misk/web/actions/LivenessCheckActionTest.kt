package misk.web.actions

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ServiceManager
import misk.MiskModule
import misk.services.FakeServiceModule
import misk.testing.InjectionTestRule
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

class LivenessCheckActionTest {
    @Rule
    @JvmField
    val testRule = InjectionTestRule(
            MiskModule(),
            FakeServiceModule()
    )

    @Inject lateinit var livenessCheckAction: LivenessCheckAction
    @Inject lateinit var serviceManager: ServiceManager

    @Test
    fun livenessDependsOnServiceState() {
        serviceManager.startAsync()
        serviceManager.awaitHealthy()
        assertThat(livenessCheckAction.livenessCheck().statusCode).isEqualTo(200)
        serviceManager.stopAsync()
        serviceManager.awaitStopped()
        assertThat(livenessCheckAction.livenessCheck().statusCode).isEqualTo(503)
    }
}
