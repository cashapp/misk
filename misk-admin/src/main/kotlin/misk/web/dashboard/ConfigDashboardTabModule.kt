package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.metadata.config.ConfigMetadataAction.ConfigTabMode.SAFE

/**
 * Installs Config dashboard tab which shows the raw config inputs
 * and the merged runtime config for your Misk service.
 *
 * [mode] If you have config parameters that include secrets, you should only install this tab
 *    in [SAFE] mode because the Misk secrets will be visible at runtime in the admin dashboard.
 *
 * TODO fix Misk.Secrets redaction in config, maybe with !secret yaml parser
 */
class ConfigDashboardTabModule(
  private val isDevelopment: Boolean,
  /** DO NOT change default of [SAFE] until redaction of Misk.Secrets and other YAML is added. */
  private val mode: ConfigMetadataAction.ConfigTabMode = SAFE,
) : KAbstractModule() {
  override fun configure() {
    bind<ConfigMetadataAction.ConfigTabMode>().toInstance(mode)
    install(WebActionModule.create<ConfigMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "config",
        url_path_prefix = "/_admin/config/",
        name = "Config",
        category = "Container Admin"
      )
    )
    install(
      WebTabResourceModule(
        isDevelopment = isDevelopment,
        slug = "config",
        web_proxy_url = "http://localhost:3200/"
      )
    )
  }
}
