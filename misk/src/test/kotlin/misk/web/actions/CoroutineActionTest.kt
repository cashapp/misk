package misk.web.actions

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import com.squareup.protos.test.grpc.HelloReply
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.currentCoroutineContext
import misk.MiskTestingServiceModule
import misk.moshi.adapter
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

@MiskTest(startService = true)
class CoroutineActionTest {
  @MiskTestModule
  val module = Modules.combine(
    WebServerTestingModule(),
    MiskTestingServiceModule(),
    WebActionModule.create<SuspendGreetWebAction>(),
  )

  @Inject private lateinit var jetty: JettyService

  @Inject private lateinit var moshi: Moshi

  private val httpClient = OkHttpClient()

  @Test
  fun `verify calling suspend function in web action can access current context`() {
    val request = get("/suspend_greet")
    val response = httpClient.newCall(request).execute()
    assertThat(response.code).isEqualTo(200)
    val body = moshi.adapter<HelloReply>().fromJson(response.body!!.string())!!
    assertThat(body.message).isEqualTo(
      buildString {
        append("Hola: ")
        append("CoroutineName(WebAction: ")
          .append(SuspendGreetWebAction::class.simpleName)
          .append(".")
          .append(SuspendGreetWebAction::hello.name)
          .append(")")
      }
    )
  }

  @Singleton
  internal class SuspendGreetWebAction @Inject constructor() : WebAction {
    @Unauthenticated
    @Get("/suspend_greet")
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    suspend fun hello(): HelloReply {
      // Without properly wrapping a call to this function in a coroutine scope,
      // [currentCoroutineContext] will throw an exception.
      val currentContext = currentCoroutineContext()
      return HelloReply("Hola: ${currentContext[CoroutineName]}")
    }
  }

  private fun get(path: String): Request {
    return Request.Builder()
      .get()
      .url(jetty.httpServerUrl.newBuilder().encodedPath(path).build())
      .build()
  }
}
