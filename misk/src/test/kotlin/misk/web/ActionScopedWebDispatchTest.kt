package misk.web

import com.google.inject.Key
import misk.Action
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.keyOf
import misk.scope.ActionScoped
import misk.scope.ActionScopedProvider
import misk.scope.ActionScopedProviderModule
import misk.scope.SeedDataTransformer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
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
  val module = TestModule()

  @Inject private lateinit var jettyService: JettyService

  @Test
  fun exposesActionScopedInInterceptors() {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(
      okhttp3.Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath("/hello").build())
        .addHeader("Security-ID", "Thor")
        .build()
    )
      .execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).isEqualTo("hello Thor")
  }

  @Test
  fun actionScopeSeedDataCanBeModified() {
    val httpClient = OkHttpClient()
    val response = httpClient.newCall(
      okhttp3.Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath("/hello").build())
        .addHeader("Principal-Seed", "Thor")
        .build()
    )
      .execute()
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body!!.string()).isEqualTo("hello Thor")
  }

  @Singleton
  class FakeIdentityActionScopedProvider @Inject internal constructor(
    private val httpCall: ActionScoped<HttpCall>
  ) : ActionScopedProvider<Principal> {
    override fun get(): Principal = Principal {
      httpCall.get().requestHeaders["Security-Id"] ?: ""
    }
  }

  @Singleton
  class Hello @Inject internal constructor(
    private val principal: ActionScoped<Principal>
  ) : WebAction {
    @Get("/hello")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun hello(): String = "hello ${principal.get().name}"
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<Hello>())
      install(object : ActionScopedProviderModule() {
        override fun configureProviders() {
          bindProvider(Principal::class, FakeIdentityActionScopedProvider::class)
        }
      })
      val principalSeeder = object : SeedDataTransformer {
        override fun transform(
          seedData: Map<Key<*>, Any?>,
        ): Map<Key<*>, Any?> {
          val httpCall = seedData.getValue(keyOf<HttpCall>()) as HttpCall
          val principalSeed = httpCall.requestHeaders["Principal-Seed"] ?: return seedData
          val principal = Principal { principalSeed }
          return seedData + mapOf(Key.get(Principal::class.java) to principal)
        }
      }

      val principalSeederWebSeedDataTransformerFactory = object : WebActionSeedDataTransformerFactory {
        override fun create(
          pathPattern: PathPattern,
          action: Action
        ): SeedDataTransformer {
          return principalSeeder
        }
      }

      multibind<WebActionSeedDataTransformerFactory>().toInstance(principalSeederWebSeedDataTransformerFactory)
    }
  }
}
