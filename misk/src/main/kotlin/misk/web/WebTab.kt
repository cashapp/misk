package misk.web

import misk.MiskCaller

abstract class WebTab(
  slug: String,
  url_path_prefix: String,
  // roles, services permissions control visibility of tab to misk web application user
  // it does not deal with any other permissions such as static resource access or otherwise
  val roles: Set<String> = setOf(),
  val services: Set<String> = setOf()
) : ValidWebEntry(slug = slug, url_path_prefix = url_path_prefix) {
  fun isAuthenticated(caller: MiskCaller?): Boolean {
    return when {
      roles.isEmpty() && services.isEmpty() -> true       // no role/service requirement => unauthenticated requests allowed (including when caller is null)
      caller == null -> false                             // role/service requirement present but caller null => assume authentication broken
      roles.any { caller.roles.contains(it) } -> true     // matching role
      services.any { caller.service == it } -> true       // matching service
      else -> false
    }
  }
}

class DashboardTab(
  slug: String,
  url_path_prefix: String,
  val name: String,
  val category: String = "Container Admin",
  roles: Set<String> = setOf(),
  services: Set<String> = setOf()
) : WebTab(slug = slug, url_path_prefix = url_path_prefix, roles = roles, services = services)
