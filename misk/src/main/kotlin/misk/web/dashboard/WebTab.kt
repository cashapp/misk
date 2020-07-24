package misk.web.dashboard

abstract class WebTab(
  slug: String,
  url_path_prefix: String,
  // capabilities, services permissions control visibility of tab to misk web application user
  // it does not deal with any other permissions such as static resource access or otherwise
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf()
) : ValidWebEntry(slug = slug, url_path_prefix = url_path_prefix)
