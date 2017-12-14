package misk.web.actions

import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ServiceManager
import misk.MiskModule
import misk.services.FakeServiceModule
import misk.testing.MiskTest
import misk.testing.ModuleProvider
import misk.testing.Modules
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class LivenessCheckActionTest {
    @Modules val modules = ModuleProvider(MiskModule::class, FakeServiceModule::class)

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
