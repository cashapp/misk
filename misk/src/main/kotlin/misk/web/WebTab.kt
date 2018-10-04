package misk.web

abstract class WebTab(
  slug: String,
  url_path_prefix: String
) : ValidWebEntry(slug = slug, url_path_prefix = url_path_prefix)