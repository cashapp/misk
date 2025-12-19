package misk.web.metadata.config

import com.google.inject.Provider
import com.squareup.moshi.Moshi
import jakarta.inject.Inject
import java.lang.management.RuntimeMXBean
import javax.management.ObjectName
import misk.inject.KAbstractModule
import misk.moshi.MoshiModule
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.jvm.JvmMetadata
import misk.web.metadata.jvm.JvmMetadataProvider
import misk.web.metadata.jvm.JvmRuntime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
class JvmMetadataTest {
  @MiskTestModule val module = JvmMetadataTestingModule()

  @Inject lateinit var moshi: Moshi
  @Inject internal lateinit var jvmMetadataProvider: Provider<JvmMetadata>

  /** Sanity check that we're able to call the action and spot check the expected data */
  @Test
  fun golden() {
    val rawResponse = jvmMetadataProvider.get().prettyPrint
    val response = moshi.adapter<JvmRuntime>().fromJson(rawResponse)!!
    assertThat(response.vm_name).isEqualTo("FakeRuntimeMxBean - VM Name")
    assertThat(response).isEqualTo(JvmRuntime.create(FakeRuntimeMxBean()))
  }

  class JvmMetadataTestingModule : KAbstractModule() {
    override fun configure() {
      install(MoshiModule())
      bind<RuntimeMXBean>().to<FakeRuntimeMxBean>()
      bind<JvmMetadata>().toProvider(JvmMetadataProvider())
    }
  }

  class FakeRuntimeMxBean @Inject constructor() : RuntimeMXBean {
    override fun getObjectName(): ObjectName = ObjectName.getInstance("FakeRuntimeMxBean - object name")

    override fun getName(): String = "FakeRuntimeMxBean - Name"

    override fun getVmName(): String = "FakeRuntimeMxBean - VM Name"

    override fun getVmVendor(): String = "FakeRuntimeMxBean - VM Vendor"

    override fun getVmVersion(): String = "FakeRuntimeMxBean - VM Version"

    override fun getSpecName(): String = "FakeRuntimeMxBean - Spec Name"

    override fun getSpecVendor(): String = "FakeRuntimeMxBean - Spec Vendor"

    override fun getSpecVersion(): String = "FakeRuntimeMxBean - Spec Version"

    override fun getManagementSpecVersion(): String = "FakeRuntimeMxBean - Management Spec Version"

    override fun getClassPath(): String = "FakeRuntimeMxBean - Class Path"

    override fun getLibraryPath(): String = "FakeRuntimeMxBean - Library Path"

    override fun isBootClassPathSupported(): Boolean = true

    override fun getBootClassPath(): String = "FakeRuntimeMxBean - Boot Class Path"

    override fun getInputArguments(): MutableList<String> =
      mutableListOf("FakeRuntimeMxBean - arg 1", "FakeRuntimeMxBean - arg 2")

    override fun getUptime(): Long = 37

    override fun getStartTime(): Long = 17

    override fun getSystemProperties(): MutableMap<String, String> =
      mutableMapOf(
        "FakeRuntimeMxBean prop key 1" to "FakeRuntimeMxBean prop value 1",
        "FakeRuntimeMxBean prop key 2" to "FakeRuntimeMxBean prop value 2",
      )
  }
}
