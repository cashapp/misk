package misk.web.dashboard

data class AdminDashboardProtobufDocUrlPrefix(
  val url: String?
) : ValidWebEntry(url_path_prefix = url ?: "/")
