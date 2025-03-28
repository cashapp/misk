package com.squareup.exemplar

import com.google.inject.Provides
import com.squareup.exemplar.audit.ExemplarAuditClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.MiskTestingServiceModule
import misk.audit.AuditClientConfig
import misk.config.AppNameModule
import misk.environment.DeploymentModule
import misk.inject.KAbstractModule
import misk.logging.LogCollectorModule
import misk.security.authz.AccessControlModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.MiskWebModule
import misk.web.WebConfig
import misk.web.WithMiskCaller
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.logging.LogCollector

@MiskTest(startService = true)
@WithMiskCaller
class ExemplarAuditClientTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var client: ExemplarAuditClient
  @Inject lateinit var mockWebServer: MockWebServer
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun `happy path`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(200)
    )
    client.logEvent(
      target = "the-database-001",
      description = "description",
    )
    assertThat(logCollector.takeMessages(ExemplarAuditClient::class)).isEmpty()
  }

  @Test
  fun `logs error on 400`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(400)
    )
    client.logEvent(
      target = "the-database-001",
      description = "description",
    )
    assertThat(logCollector.takeMessage(ExemplarAuditClient::class).dropLast(8) + "12345/}]")
      .isEqualTo("""Failed to send audit event [event=Event(eventSource=test-app, eventTarget=the-database-001, timestampSent=2147483647, applicationName=test-app, applicationTier=null, approverLDAP=default-user, automatedChange=false, description=description, richDescription=null, environment=testing, detailURL=null, region=us-west-2, requestorLDAP=default-user)][response=Response{protocol=http/1.1, code=400, message=Client Error, url=http://localhost:12345/}]""")
  }

  @Test
  fun `logs error on 500`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(500)
    )
    client.logEvent(
      target = "the-database-001",
      description = "description",
    )
    assertThat(logCollector.takeMessage(ExemplarAuditClient::class).dropLast(8) + "12345/}]")
      .isEqualTo("""Failed to send audit event [event=Event(eventSource=test-app, eventTarget=the-database-001, timestampSent=2147483647, applicationName=test-app, applicationTier=null, approverLDAP=default-user, automatedChange=false, description=description, richDescription=null, environment=testing, detailURL=null, region=us-west-2, requestorLDAP=default-user)][response=Response{protocol=http/1.1, code=500, message=Server Error, url=http://localhost:12345/}]""")
  }

  @Test
  fun `set parameters override defaults`() {
    mockWebServer.enqueue(
      MockResponse()
        .setResponseCode(200)
    )
    client.logEvent(
      target = "the-database-001",
      description = "description",
      automatedChange = true,
      richDescription = "longer description",
      detailURL = "http://localhost",
      approverLDAP = "override-approver",
      requestorLDAP = "override-requestor",
      applicationName = "override-app",
    )

    val recordedRequest: RecordedRequest = mockWebServer.takeRequest()
    assertThat(recordedRequest.method).isEqualTo("POST")
    assertThat(recordedRequest.path).isEqualTo("/")
    assertThat(recordedRequest.getHeader("Content-Type")).contains("application/json")
    assertThat(recordedRequest.body.readUtf8())
      .isEqualTo(
        """{"eventSource":"test-app","eventTarget":"the-database-001","timestampSent":2147483647,"applicationName":"override-app","approverLDAP":"override-approver","automatedChange":true,"description":"description","richDescription":"longer description","environment":"testing","detailURL":"http://localhost","region":"us-west-2","requestorLDAP":"override-requestor"}"""
      )
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AppNameModule("test-app"))
      install(MiskWebModule(WebConfig(0)))
      install(AccessControlModule())
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
      bind<MockWebServer>().toInstance(MockWebServer())
      install(LogCollectorModule())
    }

    @Provides
    @Singleton
    fun provideAuditCLientConfig(server: MockWebServer): AuditClientConfig {
      val url = server.url("/")
      return AuditClientConfig(
        url = url.toString()
      )
    }
  }
}
