package misk.web.metadata.config

import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.moshi.MoshiModule
import misk.moshi.adapter
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.metadata.jvm.JvmMetadataAction
import misk.web.metadata.jvm.JvmMetadataAction.JvmRuntimeResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.management.RuntimeMXBean
import javax.inject.Inject
import javax.management.ObjectName

@MiskTest(startService = false)
class JvmMetadataActionTest {
  @MiskTestModule
  val module = JvmMetadataTestingModule()

  @Inject lateinit var moshi: Moshi
  @Inject lateinit var jvmMetadataAction: JvmMetadataAction

  /** Sanity check that we're able to call the action and spot check the expected data */
  @Test fun golden() {
    val rawResponse = jvmMetadataAction.getRuntime();
    val response = moshi.adapter<JvmRuntimeResponse>().fromJson(rawResponse)!!
    assertThat(response.vm_name).isEqualTo("FakeRuntimeMxBean - VM Name")
    assertThat(response).isEqualTo(JvmMetadataAction.JvmRuntimeResponse.create(FakeRuntimeMxBean()))
  }

  class JvmMetadataTestingModule : KAbstractModule() {
    override fun configure() {
      install(MoshiModule())
      bind<RuntimeMXBean>().to<FakeRuntimeMxBean>()
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
      mutableListOf<String>("FakeRuntimeMxBean - arg 1", "FakeRuntimeMxBean - arg 2")

    override fun getUptime(): Long = 37

    override fun getStartTime(): Long = 17

    override fun getSystemProperties(): MutableMap<String, String> {
      return mutableMapOf(
        "FakeRuntimeMxBean prop key 1" to "FakeRuntimeMxBean prop value 1",
        "FakeRuntimeMxBean prop key 2" to "FakeRuntimeMxBean prop value 2",
      )
    }
  }
}
