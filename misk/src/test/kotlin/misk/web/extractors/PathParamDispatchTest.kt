package misk.web.extractors

import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.TestWebModule
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class PathParamDispatchTest {
    @MiskTestModule
    val module = Modules.combine(
            MiskModule(),
            WebModule(),
            TestWebModule(),
            TestModule())

    @Inject
    lateinit var moshi: Moshi

    @Inject
    lateinit var jettyService: JettyService

    enum class ObjectType {
        USER,
        FILE,
        FOLDER
    }

    @Test
    fun pathParamsConvertToProperTypes() {
        val response = get("/objects/FILE/defaults/245")
        assertThat(response.code()).isEqualTo(200)
        assertThat(response.body()?.string()).isEqualTo("(type=FILE,name=defaults,version=245)")
    }

    @Test
    fun pathParamsSupportExplicitPathNames() {
        val response = get("/custom-named-route")
        assertThat(response.code()).isEqualTo(200)
        assertThat(response.body()?.string()).isEqualTo("routing to custom-named-route")
    }

    class TestModule : KAbstractModule() {
        override fun configure() {
            install(WebActionModule.create<GetObjectDetails>())
            install(WebActionModule.create<CustomPathParamName>())
        }
    }

    class GetObjectDetails : WebAction {
        @Get("/objects/{objectType}/{name}/{version}")
        @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
        fun getObjectDetails(
                @PathParam objectType: ObjectType,
                @PathParam name: String,
                @PathParam version: Long
        ): String = "(type=$objectType,name=$name,version=$version)"
    }

    class CustomPathParamName : WebAction {
        @Get("/{router}")
        @ResponseContentType(MediaTypes.TEXT_PLAIN_UTF8)
        fun router(@PathParam("router") routeName: String) = "routing to ${routeName}"
    }

    fun get(path: String) : okhttp3.Response {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .get()
                .url(serverUrlBuilder().encodedPath(path).build())
                .build()
        return httpClient.newCall(request).execute()
    }

    private fun serverUrlBuilder(): HttpUrl.Builder {
        return jettyService.serverUrl.newBuilder()
    }
}