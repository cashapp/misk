package misk.healthchecks

import misk.web.Get
import misk.web.Response
import misk.web.ResponseBody
import misk.web.StaticResourceMapper
import misk.web.actions.WebAction
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClusterWideHealthPageAction : WebAction {
  @Inject lateinit var staticResourceMapper: StaticResourceMapper

  // TODO(tso): should this just be for all of misk? do react routing? probably
  @Get("/health")
  fun health(): Response<ResponseBody> {
    return staticResourceMapper.getResponse("/admin/index.html")!!
  }
}
