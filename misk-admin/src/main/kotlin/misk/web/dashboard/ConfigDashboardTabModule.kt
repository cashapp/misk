package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.jvm.JvmManagementFactoryModule
import misk.web.WebActionModule
import misk.web.metadata.config.ConfigMetadata
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.metadata.config.ConfigMetadataAction.ConfigTabMode.SAFE
import misk.web.metadata.config.ConfigMetadataProvider

/**
 * Installs Config dashboard tab which shows the raw config inputs
 * and the merged runtime config for your Misk service.
 *
 * [mode] If you have config parameters that include secrets, you should only install this tab
 *    in [SAFE] mode because the Misk secrets will be visible at runtime in the admin dashboard.
 */
class ConfigDashboardTabModule @JvmOverloads constructor(
  private val isDevelopment: Boolean,
  /** DO NOT change default of [SAFE] until redaction of Misk.Secrets and other YAML is added. */
  private val mode: ConfigMetadataAction.ConfigTabMode = SAFE,
) : KAbstractModule() {
  override fun configure() {
    install(JvmManagementFactoryModule())

    bind<ConfigMetadataAction.ConfigTabMode>().toInstance(mode)
    bind<ConfigMetadata>().toProvider(ConfigMetadataProvider())
    install(WebActionModule.create<ConfigMetadataAction>())

    install(DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
      isDevelopment = isDevelopment,
      slug = "config",
      urlPathPrefix = "/_admin/config/",
      developmentWebProxyUrl = "http://localhost:3200/",
      menuLabel = "Config",
      menuCategory = "Container Admin"
    ))
  }
}
