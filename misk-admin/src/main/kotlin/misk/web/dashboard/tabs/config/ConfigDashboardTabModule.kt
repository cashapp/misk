package misk.web.dashboard.tabs.config

import misk.inject.KAbstractModule
import misk.jvm.JvmManagementFactoryModule
import misk.web.WebActionModule
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.AdminDashboardModule.Companion.DEFAULT_TAB_CATEGORY
import misk.web.dashboard.DashboardModule
import misk.web.metadata.config.ConfigMetadataAction
import misk.web.metadata.config.ConfigMetadataAction.ConfigTabMode.SAFE

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
    install(WebActionModule.create<ConfigMetadataAction>())

    // Tab v2
    install(WebActionModule.create<ConfigIndexAction>())
    install(
      DashboardModule.createHotwireTab<AdminDashboard, AdminDashboardAccess>(
        slug = "misk-config",
        urlPathPrefix = ConfigIndexAction.PATH,
        menuLabel = "Config",
        menuCategory = DEFAULT_TAB_CATEGORY
      )
    )

    // Tab v1
    install(
      DashboardModule.createMiskWebTab<AdminDashboard, AdminDashboardAccess>(
        isDevelopment = isDevelopment,
        slug = "config",
        urlPathPrefix = "/_admin/config-v1/",
        developmentWebProxyUrl = "http://localhost:3200/",
        menuLabel = "Config v1",
        menuCategory = DEFAULT_TAB_CATEGORY
      )
    )
  }
}
