package misk.metrics.backends.stackdriver

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.monitoring.v3.Monitoring
import com.google.common.util.concurrent.Service
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import misk.config.AppName
import misk.inject.addMultibinderBinding
import misk.inject.asSingleton
import misk.inject.to

class StackDriverBackendModule : AbstractModule() {
  override fun configure() {
    bind(StackDriverSender::class.java)
        .to(StackDriverBatchedSender::class.java)
        .asSingleton()

    binder().addMultibinderBinding<Service>().to<StackDriverReporterService>()
  }

  @Provides
  @Singleton
  fun monitoring(@AppName appName: String, config: StackDriverBackendConfig): Monitoring =
      Monitoring.Builder(GoogleNetHttpTransport.newTrustedTransport(), JacksonFactory(), null)
          .setApplicationName(appName)
          .setServicePath(config.service_path)
          .setRootUrl(config.root_url)
          .build()

}
