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
  fun isAuthenticated(caller: MiskCaller?): Boolean {
    return when {
      capabilities.isEmpty() && services.isEmpty() -> true // no capabilities/service requirement => unauthenticated requests allowed (including when caller is null)
      caller == null -> false // capability/service requirement present but caller null => assume authentication broken
      capabilities.any { caller.capabilities.contains(it) } -> true // matching capability
      services.any { caller.service == it } -> true // matching service
      else -> false
    }
  }
}

class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val name: String,
  val category: String = "Container Admin",
  capabilities: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug, url_path_prefix, capabilities, services)
