package misk.web.dev

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.time.FakeClock
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@MiskTest(startService = true)
internal class DevCheckReloadActionTest {
  @MiskTestModule
  val module = object : misk.inject.KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(DevModule())
    }
  }

  @Inject lateinit var jetty: JettyService
  @Inject lateinit var httpClientFactory: HttpClientFactory
  @Inject lateinit var reloadSignalService: ReloadSignalService

  private lateinit var client: OkHttpClient

  @BeforeEach
  fun setUp() {
    client = httpClientFactory.create(HttpClientEndpointConfig(jetty.httpServerUrl.toString()))
  }

  @Test
  fun returns200WhenMarkerIsMissing() {
    val url = jetty.httpServerUrl.resolve("/_dev/check-reload")!!
    val response = client.newCall(
      Request.Builder()
        .url(url)
        .build()
    ).execute()

    assertEquals(200, response.code)

    val marker = reloadSignalService.lastLoadTimestamp.toEpochMilli().toString()
    val body = response.body.string()
    assertEquals(marker, body)
    assertEquals(marker, response.header("ETag"))
    assertEquals("no-cache, max-age=0, must-revalidate", response.header("Cache-Control"))
  }

  @Test
  fun returns200WhenMarkerDiffers() {
    val url = jetty.httpServerUrl.resolve("/_dev/check-reload")!!
    val response = client.newCall(
      Request.Builder()
        .url(url)
        .header("If-None-Match", "-1") // guaranteed to differ
        .build()
    ).execute()

    assertEquals(200, response.code)

    val marker = reloadSignalService.lastLoadTimestamp.toEpochMilli().toString()
    val body = response.body.string()
    assertEquals(marker, body)
    assertEquals(marker, response.header("ETag"))
    assertEquals("no-cache, max-age=0, must-revalidate", response.header("Cache-Control"))
  }

  @Test
  fun returns304WhenEqualMarkerAndServiceIsShuttingDown() {
    val marker = reloadSignalService.lastLoadTimestamp.toEpochMilli().toString()
    reloadSignalService.stopAsync() // simulate service shutdown

    val url = jetty.httpServerUrl.resolve("/_dev/check-reload")!!
    val response = client.newCall(
      Request.Builder()
        .url(url)
        .header("If-None-Match", marker)
        .build()
    ).execute()

    assertEquals(304, response.code)
  }

  @Test
  fun returns304WhenEqualMarkerAndDeadlineIsExceeded() {
    val marker = reloadSignalService.lastLoadTimestamp.toEpochMilli().toString()

    val url = jetty.httpServerUrl.resolve("/_dev/check-reload?timeout=100")!!
    val request = Request.Builder()
      .url(url)
      .header("If-None-Match", marker)
      .build()

    val response = client.newCall(request).execute()

    assertEquals(304, response.code)
  }
}
