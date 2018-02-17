package misk.cloud.gcp.environment

import misk.environment.InstanceMetadata
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Provider

internal class GcpInstanceMetadataProvider @Inject internal constructor(
    val serverUrl: String = GCP_INSTANCE_METADATA_SERVER
) : Provider<InstanceMetadata> {

  companion object {
    val GCP_INSTANCE_METADATA_SERVER = "http://metadata.google.internal"
    val GCP_INSTANCE_METADATA_PATH = "computeMetadata/v1/instance"
  }

  override fun get(): InstanceMetadata {
    var instanceName = getMetadata("name")
    if (instanceName.isBlank()) {
      instanceName = getMetadata("id")
    }

    val zone = getMetadata("zone").split('/')
        .last()
    return InstanceMetadata(instanceName, zone)
  }

  fun getMetadata(metadataCategory: String): String {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .get()
        .url("$serverUrl/$GCP_INSTANCE_METADATA_PATH/$metadataCategory")
        .build()

    val response = httpClient.newCall(request)
        .execute()
        .body()
    return response?.string()
        ?: throw IllegalStateException("could not retrieve $metadataCategory")
  }

}
