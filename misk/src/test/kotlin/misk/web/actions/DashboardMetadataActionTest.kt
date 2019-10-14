package misk.web.actions

import com.squareup.moshi.Moshi
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.moshi.adapter
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.DashboardTab
import misk.web.jetty.JettyService
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@MiskTest(startService = true)
class DashboardMetadataActionTest {
  @MiskTestModule
  val module = AdminDashboardActionTestingModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var httpClientFactory: HttpClientFactory
  @Inject private lateinit var dashboardTabs: List<DashboardTab>

  val path = "/api/admindashboardtabs/"
  val defaultDashboard = "AdminDashboardTab"

  @Test fun customCapabilityAccess_unauthenticated() {
    val response = executeRequest(path = path + defaultDashboard)
    assertEquals(0, response.adminDashboardTabs.size)
  }

  @Test fun customCapabilityAccess_unauthorized() {
    val response = executeRequest(
      path = path + defaultDashboard,
      user = "sandy",
      capabilities = "guest"
    )
    assertEquals(0, response.adminDashboardTabs.size)
  }

  @Test fun customCapabilityAccess_authorized() {
    val response = executeRequest(
      path = path + defaultDashboard,
      user = "sandy",
      capabilities = "admin_access"
    )
    assertEquals(2, response.adminDashboardTabs.size)
    assertNotNull(response.adminDashboardTabs.find { it.name == "Config" })
    assertNotNull(response.adminDashboardTabs.find { it.name == "Web Actions" })
  }

  @Test fun loadOtherDashboardTabs() {
    val dashboard = DashboardMetadataActionTestDashboard::class.simpleName!!
    val response = executeRequest(
      path = path + dashboard,
      user = "sandy",
      capabilities = "test_admin_access"
    )
    assertEquals(1, response.adminDashboardTabs.size)
    assertNotNull(response.adminDashboardTabs.find { it.name == "Test Dashboard Tab" })
  }

  private fun executeRequest(
    path: String = "/",
    service: String? = null,
    user: String? = null,
    capabilities: String? = null
  ): DashboardMetadataAction.Response {
    val client = createOkHttpClient()

    val baseUrl = jetty.httpServerUrl
    val requestBuilder = Request.Builder()
      .url(baseUrl.resolve(path)!!)
    service?.let {
      requestBuilder.header(FakeCallerAuthenticator.SERVICE_HEADER, service)
    }
    user?.let {
      requestBuilder.header(FakeCallerAuthenticator.USER_HEADER, user)
    }
    capabilities?.let {
      requestBuilder.header(FakeCallerAuthenticator.CAPABILITIES_HEADER, capabilities)
    }
    val call = client.newCall(requestBuilder.build())
    val response = call.execute()

    val moshi = Moshi.Builder().build()
    val responseAdaptor = moshi.adapter<DashboardMetadataAction.Response>()
    return responseAdaptor.fromJson(response.body!!.source())!!
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }
}
