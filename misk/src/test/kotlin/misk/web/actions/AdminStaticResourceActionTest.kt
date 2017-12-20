package misk.web.actions

import com.google.common.util.concurrent.Service
import com.google.inject.AbstractModule
import com.google.inject.util.Modules
import misk.MiskModule
import misk.admin.actions.AdminStaticResourceAction
import misk.inject.addMultibinderBinding
import misk.inject.to
import misk.services.FakeService
import misk.templating.TemplatingModule
import misk.testing.ActionTest
import misk.testing.ActionTestModule
import okhttp3.Headers
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import javax.inject.Inject

@ActionTest
class AdminStaticResourceActionTest {
    @ActionTestModule
    val module = Modules.combine(MiskModule(), TemplatingModule(), object : AbstractModule() {
        override fun configure() {
            binder().addMultibinderBinding<Service>().to<FakeService>()
        }
    })

    @Inject lateinit var resourceAction: AdminStaticResourceAction

    private val buffer = Buffer()

    @Before
    fun before() {
        buffer.clear()
    }

    @Test
    fun cssTest() {
        checkResponse("test", "css", "css")
    }

    @Test
    fun htmlTest() {
        checkResponse("test", "html", "html")
    }

    @Test
    fun jsTest() {
        checkResponse("test", "js", "javascript")
    }

    @Test
    fun resourceNotFound() {
        try {
            (resourceAction.staticResource("not-found", "css"))
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).isEqualTo("Could not find resource at admin/css/not-found.css")
        }
    }


    private fun checkResponse(resourceName: String, fileType: String, mimeType: String) {
        val response = resourceAction.staticResource(resourceName, fileType)
        assertThat(response.headers).isEqualTo(Headers.of("Content-Type", "text/$mimeType; charset=utf-8"))
        response.body.writeTo(buffer)
        assertThat(buffer.toString()).isEqualTo("[text=$fileType\\n]")
    }

}
