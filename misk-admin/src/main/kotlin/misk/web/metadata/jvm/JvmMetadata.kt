package misk.web.metadata.jvm

import jakarta.inject.Inject
import java.lang.management.RuntimeMXBean
import misk.moshi.adapter
import misk.web.metadata.Metadata
import misk.web.metadata.MetadataProvider
import misk.web.metadata.toFormattedJson
import wisp.moshi.defaultKotlinMoshi

internal data class JvmMetadata(val jvmRuntime: JvmRuntime) :
  Metadata(
    metadata = jvmRuntime,
    prettyPrint = defaultKotlinMoshi.adapter<JvmRuntime>().toFormattedJson(jvmRuntime),
    descriptionString = "JVM runtime MX bean configuration.",
  )

internal class JvmMetadataProvider : MetadataProvider<JvmMetadata> {
  @Inject private lateinit var runtimeMxBean: RuntimeMXBean

  override val id: String = "jvm"

  override fun get() = JvmMetadata(jvmRuntime = JvmRuntime.create(runtimeMxBean))
}

/** A data class for serializing information from [java.lang.management.RuntimeMxBean] */
internal data class JvmRuntime(
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
    fun create(runtimeMxBean: RuntimeMXBean): JvmRuntime {
      return JvmRuntime(
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
        boot_class_path =
          if (runtimeMxBean.isBootClassPathSupported) {
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
