package misk.cloud.aws.environment

import misk.environment.InstanceMetadata
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Provider

internal class AwsInstanceMetadataProvider(
    val serverUrl: String = AWS_INSTANCE_METADATA_SERVER
) : Provider<InstanceMetadata> {

  companion object {
    val AWS_INSTANCE_METADATA_SERVER = "http://169.254.169.254"
    val AWS_INSTANCE_METADATA_PATH = "latest/meta-data"
  }

  override fun get(): InstanceMetadata {
    val instanceName = getMetadata("instance-id")
    val zone = getMetadata("placement/availability-zone")
    return InstanceMetadata(instanceName, zone)
  }

  private fun getMetadata(metadataCategory: String): String {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
        .get()
        .url("$serverUrl/$AWS_INSTANCE_METADATA_PATH/$metadataCategory")
        .build()

    val response = httpClient.newCall(request)
        .execute()
        .body()
    return response?.string()
        ?: throw IllegalStateException("could not retrieve $metadataCategory")
  }
}
