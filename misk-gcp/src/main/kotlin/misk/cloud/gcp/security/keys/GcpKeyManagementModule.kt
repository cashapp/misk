package misk.cloud.gcp.security.keys

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.cloudkms.v1.CloudKMS
import com.google.inject.Provides
import misk.config.AppName
import misk.inject.KAbstractModule
import misk.inject.asSingleton
import misk.security.keys.KeyService
import javax.inject.Singleton

class GcpKeyManagementModule(private val config: GcpKmsConfig) : KAbstractModule() {
  override fun configure() {
    bind<GcpKmsConfig>().toInstance(config)
    bind<KeyService>().to<GcpKeyService>().asSingleton()
  }

  @Provides
  @Singleton
  fun providesKms(@AppName appName: String): CloudKMS =
      CloudKMS.Builder(newTrustedTransport(), JacksonFactory(), null)
          .setApplicationName(appName)
          .build()
}
