package misk.web

import com.google.inject.util.Modules
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.OkHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.Principal
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class ActionScopedWebDispatchTest {
  @MiskTestModule
  val module = Modules.combine(
      MiskModule(),
      WebModule(),
      TestWebModule(),
      TestModule()
  )

  private @Inject lateinit var jettyService: JettyService

  @Test
  fun exposesActionScopedInInterceptors() {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(
        okhttp3.Request.Builder()
            .url(jettyService.serverUrl.newBuilder().encodedPath("/hello").build())
            .addHeader("Security-ID", "Thor")
            .build()
    )
        .execute()
    assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()!!.string()).isEqualTo("hello Thor")
  }

  @Singleton
  class FakeIdentityActionScopedProvider @Inject internal constructor(
      private val request: ActionScoped<Request>
  ) : ActionScopedProvider<Principal> {
    override fun get(): Principal = Principal {
      request.get().headers["Security-Id"] ?: ""
    }
  }

  @Singleton
  class Hello @Inject internal constructor(
      private val principal: @JvmSuppressWildcards ActionScoped<Principal>
  ) : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun hello(): String = "hello ${principal.get().name}"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebActionModule.create<Hello>())
      install(object : ActionScopedProviderModule() {
        override fun configureProviders() {
          bindProvider(Principal::class, FakeIdentityActionScopedProvider::class)
        }
      })
    }
  }
}
