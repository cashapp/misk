package misk.environment

import com.google.inject.AbstractModule
import com.google.inject.Provides
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Singleton

/** Retrieves instance metadata for applications running on AWS */
class AwsInstanceMetadataModule : AbstractModule() {
    companion object {
        private val AWS_INSTANCE_METADATA_URL = "http://169.254.169.254/latest/meta-data"
    }

    override fun configure() {}

    @Provides
    @Singleton
    fun providesInstanceMetadata(): InstanceMetadata {
        val instanceName = get("instance-id")
        val zone = get("placement/availability-zone")
        return InstanceMetadata(instanceName, zone)
    }

    fun get(metadataCategory: String): String {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .get()
                .url("$AWS_INSTANCE_METADATA_URL/$metadataCategory")
                .build()

        val response = httpClient.newCall(request).execute().body()
        return response?.string()
                ?: throw IllegalStateException("could not retrieve $metadataCategory")
    }

}
