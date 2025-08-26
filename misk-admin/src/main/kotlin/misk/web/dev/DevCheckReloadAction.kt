package misk.web.dev

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.security.authz.Unauthenticated
import misk.web.AvailableWhenDegraded
import misk.web.Get
import misk.web.QueryParam
import misk.web.RequestHeader
import misk.web.Response
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import java.time.Clock

private const val CHECK_TIMEOUT_MS = 30_000L

@Singleton
internal class DevCheckReloadAction @Inject constructor(
  private val reloadSignalService: ReloadSignalService
) : WebAction {
  @Get("/check-reload")
  @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
  @Unauthenticated
  @AvailableWhenDegraded
  fun checkReload(
    @RequestHeader("If-None-Match") previousReloadMarker: String?,
    @QueryParam("timeout") timeout: Long = CHECK_TIMEOUT_MS,
  ): Response<Any> {
    val reloadMarker = reloadSignalService.lastLoadTimestamp.toEpochMilli().toString()

    if (previousReloadMarker != reloadMarker) {
      return Response(
        reloadMarker,
        headers = Headers.Builder()
          .add("ETag", reloadMarker)
          .add("Cache-Control", "no-cache, max-age=0, must-revalidate")
          .build(),
      )
    }

    reloadSignalService.awaitShutdown(timeout)
    return Response(Unit, statusCode = 304)
  }
}
