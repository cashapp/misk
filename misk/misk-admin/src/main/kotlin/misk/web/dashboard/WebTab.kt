package misk.web.dashboard

open class WebTab(
  open val slug: String,
  open val url_path_prefix: String,
  // capabilities, services permissions control visibility of tab to misk web application user
  // it does not deal with any other permissions such as static resource access or otherwise
  open val capabilities: Set<String> = setOf(),
  open val services: Set<String> = setOf()
) : ValidWebEntry(valid_slug = slug, valid_url_path_prefix = url_path_prefix)
