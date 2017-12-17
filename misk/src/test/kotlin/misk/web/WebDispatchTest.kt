package misk.web

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

internal class WebDispatchTest {
    data class HelloBye(val message: String)

    val jsonMediaType = MediaType.parse("application/json")

    @Rule
    @JvmField
    val misk = MiskTestRule(TestModule())

    @Inject lateinit var moshi: Moshi
    private val helloByeJsonAdapter get() = moshi.adapter(HelloBye::class.java)

    @Test
    fun post() {
        val requestContent = helloByeJsonAdapter.toJson(HelloBye("my friend"))
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .post(RequestBody.create(jsonMediaType, requestContent))
                .url(misk.serverUrl().encodedPath("/hello").build())
                .build()

        val response = httpClient.newCall(request).execute()
        assertThat(response.code()).isEqualTo(200)
        val responseContent = response.body()!!.source()
        assertThat(helloByeJsonAdapter.fromJson(responseContent)!!.message)
                .isEqualTo("post hello my friend")
    }

    @Test
    fun get() {
        val httpClient = OkHttpClient()
        val request = Request.Builder()
                .get()
                .url(misk.serverUrl().encodedPath("/hello/my_friend").build())
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
        @JsonResponseBody
        fun postHello(@JsonRequestBody request: HelloBye) =
                HelloBye("post hello ${request.message}")

    }

    class GetHello : WebAction {
        @Get("/hello/{message}")
        @JsonResponseBody
        fun postHello(@PathParam("message") message: String) =
                HelloBye("get hello $message")
    }

    class PostBye : WebAction {
        @Post("/bye")
        @JsonResponseBody
        fun postBye(@JsonRequestBody request: HelloBye) =
                HelloBye("post bye ${request.message}")
    }

    class GetBye : WebAction {
        @Get("/bye/{message}")
        @JsonResponseBody
        fun getBye(@PathParam("message") message: String) =
                HelloBye("get bye $message")
    }

}