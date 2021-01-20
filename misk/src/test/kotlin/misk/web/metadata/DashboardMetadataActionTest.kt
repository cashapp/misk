package misk.web.metadata

import com.squareup.moshi.Moshi
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.moshi.adapter
import misk.security.authz.FakeCallerAuthenticator
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.jetty.JettyService
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.ValidWebEntry.Companion.slugify
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@MiskTest
class DashboardMetadataActionTest {
  @MiskTestModule
  val module = MetadataTestingModule()

  @Inject private lateinit var jetty: JettyService
  @Inject private lateinit var httpClientFactory: HttpClientFactory
  @Inject private lateinit var moshi: Moshi

  private inline fun <reified A: Annotation>asDashboardPath() = "/api/dashboard/${slugify<A>()}/metadata"

  @Test fun `admin dashboard unauthenticated tabs`() {
    val response = executeRequest(path = asDashboardPath<AdminDashboard>())
    assertEquals(0, response.dashboardMetadata.tabs.size)
  }

  @Test fun `admin dashboard unauthorized tabs`() {
    val response = executeRequest(
      path = asDashboardPath<AdminDashboard>(),
      user = "sandy",
      capabilities = "guest"
    )
    assertEquals(0, response.dashboardMetadata.tabs.size)
  }

  @Test fun `admin dashboard authorized tabs`() {
    val response = executeRequest(
      path = asDashboardPath<AdminDashboard>(),
      user = "sandy",
      capabilities = "admin_access"
    )
    assertEquals(2, response.dashboardMetadata.tabs.size)
    assertNotNull(response.dashboardMetadata.tabs.find { it.name == "Database" })
    assertNotNull(response.dashboardMetadata.tabs.find { it.name == "Web Actions" })
  }

  @Test fun `test dashboard tabs`() {
    val response = executeRequest(
      path = asDashboardPath<DashboardMetadataActionTestDashboard>(),
      user = "sandy",
      capabilities = "test_admin_access"
    )
    assertEquals(1, response.dashboardMetadata.tabs.size)
    assertNotNull(response.dashboardMetadata.tabs.find { it.name == "Test Dashboard Tab" })
  }

  @Test fun `test dashboard navbar items`() {
    val response = executeRequest(
      path = asDashboardPath<DashboardMetadataActionTestDashboard>(),
      user = "sandy",
      capabilities = "test_admin_access"
    )
    assertEquals(1, response.dashboardMetadata.navbar_items.size)
    assertEquals("<a href=\"https://cash.app/\">Test Navbar Link</a>",
      response.dashboardMetadata.navbar_items.first())
  }

  @Test fun `test dashboard navbar status`() {
    val response = executeRequest(
      path = asDashboardPath<DashboardMetadataActionTestDashboard>(),
      user = "sandy",
      capabilities = "test_admin_access"
    )
    assertEquals("Test Status", response.dashboardMetadata.navbar_status)
  }

  @Test fun `test dashboard home url`() {
    val response = executeRequest(
      path = asDashboardPath<DashboardMetadataActionTestDashboard>(),
      user = "sandy",
      capabilities = "test_admin_access"
    )
    assertEquals("/test-app/", response.dashboardMetadata.home_url)
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

    val responseAdaptor = moshi.adapter<DashboardMetadataAction.Response>()
    return responseAdaptor.fromJson(response.body!!.source())!!
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }
}
