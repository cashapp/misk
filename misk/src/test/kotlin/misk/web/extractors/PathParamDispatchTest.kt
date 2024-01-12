package misk.web.extractors

import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import misk.config.AppNameModule
import misk.feature.testing.FakeFeatureFlagsModule
import misk.feature.testing.FakeFeatureFlagsOverrideModule
import misk.web.interceptors.MiskConcurrencyLimiterEnabledFeature

@MiskTest(startService = true)
internal class PathParamDispatchTest {
  @MiskTestModule
  val module = TestModule()

  @Inject lateinit var jettyService: JettyService

  enum class ResourceType {
    USER,
    FILE,
    FOLDER
  }

  @Test
  fun pathParamsConvertToProperTypes() {
    val response = get("/objects/FILE/defaults/245")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body?.string()).isEqualTo("(type=FILE,name=defaults,version=245)")
  }

  @Test
  fun pathParamsConvertToGenericType() {
    val response = get("/objects/find/1234")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body?.string()).isEqualTo("(objectId=Id(t=1234))")
  }

  @Test
  fun pathParamsSupportExplicitPathNames() {
    val response = get("/custom-named-route")
    assertThat(response.code).isEqualTo(200)
    assertThat(response.body?.string()).isEqualTo("routing to custom-named-route")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AppNameModule("miskTest"))
      install(FakeFeatureFlagsModule())
      install(FakeFeatureFlagsOverrideModule{
        override(MiskConcurrencyLimiterEnabledFeature.ENABLED_FEATURE, true)
      })
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<GetObjectDetails>())
      install(WebActionModule.create<CustomTypeWithGenerics>())
      install(WebActionModule.create<CustomPathParamName>())
    }
  }

  class GetObjectDetails @Inject constructor() : WebAction {
    @Get("/objects/{resourceType}/{name}/{version}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun getObjectDetails(
      @PathParam resourceType: ResourceType,
      @PathParam name: String,
      @PathParam version: Long
    ): String = "(type=$resourceType,name=$name,version=$version)"
  }

  data class Id<T>(
    val t: T,
  ) {
    companion object {
      @JvmStatic
      fun valueOf(value: String): Id<Any> {
        return Id(value)
      }
    }
  }

  class CustomTypeWithGenerics @Inject constructor() : WebAction {
    @Get("/objects/find/{objectId}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun getObjectDetails(
      @PathParam objectId: Id<String>,
    ): String = "(objectId=$objectId)"
  }

  class CustomPathParamName @Inject constructor() : WebAction {
    @Get("/{router}")
    @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
    fun router(@PathParam("router") routeName: String) = "routing to $routeName"
  }

  fun get(path: String): okhttp3.Response {
    val httpClient = OkHttpClient()
    val request = Request.Builder()
      .get()
      .url(serverUrlBuilder().encodedPath(path).build())
      .build()
    return httpClient.newCall(request).execute()
  }

  private fun serverUrlBuilder(): HttpUrl.Builder {
    return jettyService.httpServerUrl.newBuilder()
  }
}
