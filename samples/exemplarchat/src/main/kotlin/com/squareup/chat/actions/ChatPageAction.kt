package com.squareup.chat.actions

import misk.web.Get
import misk.web.PathParam
import misk.web.Response
import misk.web.ResponseBody
import misk.web.resources.StaticResourceMapper
import misk.web.actions.WebAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatPageAction : WebAction {
  @Inject lateinit var staticResourceMapper: StaticResourceMapper

  @Get("/room/{name}")
  fun index(@PathParam name: String): Response<ResponseBody> {
    return staticResourceMapper.getResponse("/index.html")!!
  }
}
