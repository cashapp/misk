package misk.web

import misk.MiskCaller

abstract class WebTab(
  slug: String,
  url_path_prefix: String,
  // capabilities, services permissions control visibility of tab to misk web application user
  // it does not deal with any other permissions such as static resource access or otherwise
  val capabilities: Set<String> = setOf(),
  val services: Set<String> = setOf()
) : ValidWebEntry(slug = slug, url_path_prefix = url_path_prefix) {
  fun isAuthenticated(caller: MiskCaller?): Boolean = when {
    // no capabilities/service requirement => unauthenticated and null caller requests allowed
    capabilities.isEmpty() && services.isEmpty() -> true

    // capability/service requirement present but caller null => assume authentication broken
    caller == null -> false

    // matching capability
    capabilities.any { caller.capabilities.contains(it) } -> true

    // matching service
    services.any { caller.service == it } -> true

    else -> false
  }
}
