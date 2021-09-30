package misk.exceptions.dynamodb

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.model.CancellationReason
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_UNAVAILABLE
import javax.inject.Inject

@MiskTest(startService = true)
class DynamoDbExceptionMapperTest {
  @MiskTestModule
  val module = DynamoDbExceptionMapperTestModule()

  @Inject lateinit var jettyService: JettyService

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }

  @Test
  fun `service unavailable 503 for concurrency related TransactionCanceledException`() {
    val response = get("/throws/concurrency")
    Assertions.assertThat(response.code).isEqualTo(HTTP_UNAVAILABLE)
    Assertions.assertThat(response.body?.string()).isEqualTo(
      "DynamoDB Resource Contention Exception: " +
        "com.amazonaws.services.dynamodbv2.model." +
        "TransactionCanceledException: " +
        "Concurrency (Service: null; Status Code: 0; " +
        "Error Code: null; Request ID: null; Proxy: null)"
    )
  }

  @Test
  fun `internal server error 500 for all other TransactionCanceledException`() {
    val response = get("/throws/other")
    Assertions.assertThat(response.code).isEqualTo(HTTP_INTERNAL_ERROR)
    Assertions.assertThat(response.body?.string()).isEqualTo(
      "Internal server error: " +
        "com.amazonaws.services.dynamodbv2.model." +
        "TransactionCanceledException: " +
        "Not Concurrency (Service: null; Status Code: 0; " +
        "Error Code: null; Request ID: null; Proxy: null)"
    )
  }

  @Test
  fun `service unavailable 503 for concurrency related ClientExecutionTimeoutException`() {
    val response = get("/throws/timeout")
    Assertions.assertThat(response.code).isEqualTo(HTTP_UNAVAILABLE)
    Assertions.assertThat(response.body?.string())
      .isEqualTo(
        "DynamoDB Resource Contention Exception: " +
          "com.amazonaws.http.timers.client." +
          "ClientExecutionTimeoutException: Client execution did not complete " +
          "before the specified timeout configuration."
      )
  }

  private fun get(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .get()
      .url(serverUrlBuilder().encodedPath(path).build())
      .build()
    return httpClient.newCall(request).execute()
  }

  class ThrowsForConcurrency @Inject constructor() : WebAction {
    @Get("/throws/concurrency")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throws(): String {
      throw TransactionCanceledException("Concurrency")
        .withCancellationReasons(CancellationReason().withCode("TransactionConflict"))
    }
  }

  class ThrowsForOtherReason @Inject constructor() : WebAction {
    @Get("/throws/other")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throws(): String {
      throw TransactionCanceledException("Not Concurrency")
        .withCancellationReasons(CancellationReason().withCode("ProvisionedThroughputExceeded"))
    }
  }

  class ThrowsTimeout @Inject constructor() : WebAction {
    @Get("/throws/timeout")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun throws(): String {
      throw ClientExecutionTimeoutException(
        "Client execution did not complete before the specified timeout configuration."
      )
    }
  }

  class DynamoDbExceptionMapperTestModule : KAbstractModule() {
    override fun configure() {
      install(WebTestingModule())
      install(WebActionModule.create<ThrowsForConcurrency>())
      install(WebActionModule.create<ThrowsForOtherReason>())
      install(WebActionModule.create<ThrowsTimeout>())
      install(DynamoDbExceptionMapperModule())
    }
  }
}
