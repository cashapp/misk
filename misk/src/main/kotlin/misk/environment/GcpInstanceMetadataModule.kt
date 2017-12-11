package misk.environment

import com.google.inject.AbstractModule
import com.google.inject.Provides
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Singleton

/** Retrieves instance metadata for applications running on GCP */
class GcpInstanceMetadataModule : AbstractModule() {
    companion object {
        private val GCP_INSTANCE_METADATA_URL = "http://metadata.google.internal/computeMetadata/v1"
    }

    override fun configure() {}

    @Provides
    @Singleton
    fun providesInstanceMetadata(): InstanceMetadata {
        var instanceName = get("name")
        if (instanceName.isBlank()) {
            instanceName = get("id")
        }

        val zone = get("zone")
        return InstanceMetadata(instanceName, zone)
    }

    fun get(metadataCategory: String): String {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .get()
                .url("${GCP_INSTANCE_METADATA_URL}/$metadataCategory")
                .build()

        val response = httpClient.newCall(request).execute().body()
        return response?.string()
                ?: throw IllegalStateException("could not retrieve $metadataCategory")
    }

}
