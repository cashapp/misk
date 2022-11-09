package misk.web.metadata.jvm

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import misk.moshi.adapter
import misk.web.Get
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import java.lang.management.RuntimeMXBean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conveys information about the current JVM
 */
@Singleton
class JvmMetadataAction @Inject constructor(
  private val runtimeMxBean: RuntimeMXBean,
  moshi: Moshi
) : WebAction {
  private val moshiAdapter: JsonAdapter<JvmRuntimeResponse>

  init {
    // Indent to make the output more readable since the response can be quite large!
    moshiAdapter = moshi.adapter<JvmRuntimeResponse>().indent("  ")
  }

  @Get("/api/config/jvm/runtime")
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  @AdminDashboardAccess
  fun getRuntime(): String {
    return moshiAdapter.toJson(JvmRuntimeResponse.create(runtimeMxBean))
  }

  /**
   * A data class for serializing information from [java.lang.management.RuntimeMxBean]
   */
  data class JvmRuntimeResponse(
    val pid: Long?,
    val name: String?,
    val vm_name: String?,
    val vm_vendor: String?,
    val vm_version: String?,
    val spec_name: String?,
    val spec_vendor: String?,
    val spec_version: String?,
    val management_spec_version: String?,
    val class_path: String?,
    val library_path: String?,
    val is_boot_class_path_supported: Boolean?,
    val boot_class_path: String?,
    val input_arguments: List<String>?,
    val uptime_millis: Long?,
    val start_time_millis: Long?,
    val system_properties: Map<String, String>?,
  ) {
    companion object {
      fun create(runtimeMxBean: RuntimeMXBean): JvmRuntimeResponse {
        return JvmRuntimeResponse(
          pid = runtimeMxBean.pid,
          name = runtimeMxBean.name,
          vm_name = runtimeMxBean.vmName,
          vm_vendor = runtimeMxBean.vmVendor,
          vm_version = runtimeMxBean.vmVersion,
          spec_name = runtimeMxBean.specName,
          spec_vendor = runtimeMxBean.specVendor,
          spec_version = runtimeMxBean.specVersion,
          management_spec_version = runtimeMxBean.managementSpecVersion,
          class_path = runtimeMxBean.classPath,
          library_path = runtimeMxBean.libraryPath,
          is_boot_class_path_supported = runtimeMxBean.isBootClassPathSupported,
          boot_class_path = if (runtimeMxBean.isBootClassPathSupported) {
            runtimeMxBean.bootClassPath
          } else {
            null
          },
          input_arguments = runtimeMxBean.inputArguments,
          uptime_millis = runtimeMxBean.uptime,
          start_time_millis = runtimeMxBean.startTime,
          system_properties = runtimeMxBean.systemProperties,
        )
      }
    }
  }
}
