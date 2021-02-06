package com.squareup.exemplar.actions

import misk.security.authz.Unauthenticated
import misk.web.FormField
import misk.web.FormValue
import misk.web.Post
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EchoFormAction @Inject constructor() : WebAction {
  @Post("/hello")
  @Unauthenticated
  @RequestContentType(MediaTypes.APPLICATION_FORM_URLENCODED)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun echo(@FormValue form: Form): Form {
    return form
  }

  data class Form(
    val string: String,
    val int: Int,
    val nullable: String?,
    val optional: String = "optional",
    @FormField("list-of-strings") val list: List<String>
  )
}
