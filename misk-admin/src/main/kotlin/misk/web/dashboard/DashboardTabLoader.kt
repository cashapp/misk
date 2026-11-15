package misk.web.dashboard

sealed class DashboardTabLoader {
  /** Path prefix which resolves to the tab when visited in the browser. */
  abstract val urlPathPrefix: String

  data class HotwireTab(override val urlPathPrefix: String) : DashboardTabLoader()

  data class IframeTab(override val urlPathPrefix: String, val iframePath: String) : DashboardTabLoader()
}

// TODO consider removing after v2 rollout when there should be no difference between the entry and loader path prefix
data class DashboardTabLoaderEntry(val urlPathPrefix: String, val loader: DashboardTabLoader)
