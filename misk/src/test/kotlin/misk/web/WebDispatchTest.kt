package misk.web

import org.assertj.core.api.Assertions.assertThat
import com.google.inject.util.Modules
import com.squareup.moshi.Moshi
import misk.MiskModule
import misk.inject.KAbstractModule
import misk.testing.ActionTest
import misk.testing.ActionTestModule
import misk.testing.TestWebModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import javax.inject.Inject

@ActionTest(startService = true)
internal class WebDispatchTest {
    @ActionTestModule
    val module = Modules.combine(
            MiskModule(),
            WebModule(),
            TestWebModule(),
            TestModule())

    data class HelloBye(val message: String)

    @Inject lateinit var moshi: Moshi
    @Inject lateinit var jettyService: JettyService
    private val helloByeJsonAdapter get() = moshi.adapter(HelloBye::class.java)

    @Test
    fun post() {
        val requestContent = helloByeJsonAdapter.toJson(HelloBye("my friend"))
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .post(okhttp3.RequestBody.create(MediaTypes.APPLICATION_JSON_MEDIA_TYPE, requestContent))
                .url(serverUrlBuilder().encodedPath("/hello").build())
                .build()

        val response = httpClient.newCall(request).execute()
        assertThat(response.code()).isEqualTo(200)
        
	val responseContent  = response.body()!!.source()
        assertThat(helloByeJsonAdapter.fromJson(responseContent)!!.message).isEqualTo("post hello my friend")
    }

    @Test
    fun get() {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .get()
                .url(serverUrlBuilder().encodedPath("/hello/my_friend").build())
                .build()

        val response = httpClient.newCall(request).execute()
        assertThat(response.code()).isEqualTo(200)
        
	val responseContent = response.body()!!.source()
        assertThat(helloByeJsonAdapter.fromJson(responseContent)!!.message)
                .isEqualTo("get hello my_friend")
    }

    class TestModule : KAbstractModule() {
        override fun configure() {
            install(WebActionModule.create<PostHello>())
            install(WebActionModule.create<GetHello>())
            install(WebActionModule.create<PostBye>())
            install(WebActionModule.create<GetBye>())
        }
    }

    class PostHello : WebAction {
        @Post("/hello")
        @RequestContentType(MediaTypes.APPLICATION_JSON)
        @ResponseContentType(MediaTypes.APPLICATION_JSON)
        fun hello(@misk.web.RequestBody request: HelloBye) =
                HelloBye("post hello ${request.message}")
    }

    class GetHello : WebAction {
        @Get("/hello/{message}")
        @ResponseContentType(MediaTypes.APPLICATION_JSON)
        fun hello(@PathParam("message") message: String) =
                HelloBye("get hello $message")
    }

    class PostBye : WebAction {
        @Post("/bye")
        @RequestContentType(MediaTypes.APPLICATION_JSON)
        @ResponseContentType(MediaTypes.APPLICATION_JSON)
        fun bye(@misk.web.RequestBody request: HelloBye) =
                HelloBye("post bye ${request.message}")
    }

    class GetBye : WebAction {
        @Get("/bye/{message}")
        @ResponseContentType(MediaTypes.APPLICATION_JSON)
        fun bye(@PathParam("message") message: String) =
                HelloBye("get bye $message")
    }

    private fun serverUrlBuilder(): HttpUrl.Builder {
        return jettyService.serverUrl.newBuilder()
    }
}
