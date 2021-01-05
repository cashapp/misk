package misk.web.dashboard

import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.metadata.config.ConfigMetadataAction

/**
 * Installs Config tab for the Misk Admin Dashboard
 *
 * The Config tab shows the raw config inputs and the merged runtime config for a given service
 *
 * If you have config parameters that include secrets, you should NOT install this tab because the
 *    secrets will be visible at runtime in the admin dashboard.
 * TODO There is insufficient secrets redaction that doesn't allow flexibility in secret names when
 *    redaction is run
 */
class ConfigDashboardTabModule(private val isDevelopment: Boolean) : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ConfigMetadataAction>())
    multibind<DashboardTab>().toProvider(
      DashboardTabProvider<AdminDashboard, AdminDashboardAccess>(
        slug = "config",
        url_path_prefix = "/_admin/config/",
        name = "Config",
        category = "Container Admin"
      ))
    install(WebTabResourceModule(
      isDevelopment = isDevelopment,
      slug = "config",
      web_proxy_url = "http://localhost:3200/"
    ))
  }
}
