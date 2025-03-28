package misk.audit

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import wisp.deployment.TESTING
import wisp.logging.LogCollector

@MiskTest(startService = true)
@WithMiskCaller
class FakeAuditClientTest {
  @MiskTestModule val module = TestModule()

  @Inject lateinit var client: FakeAuditClient
  @Inject lateinit var logCollector: LogCollector

  @Test
  fun `happy path`() {
    client.logEvent(
      description = "description",
      target = "the-database-001",
    )
    assertThat(logCollector.takeMessages(FakeAuditClient::class).size).isEqualTo(0)
    assertThat(client.sentEvents).hasSize(1)
    assertThat(client.sentEvents[0]).isEqualTo(
      FakeAuditClient.FakeAuditEvent(
        eventSource = "test-app",
        eventTarget = "the-database-001",
        timestampSent = 2147483647,
        applicationName = "test-app",
        approverLDAP = "default-user",
        automatedChange = false,
        description = "description",
        richDescription = null,
        environment = "testing",
        detailURL = null,
        region = "us-west-2",
        requestorLDAP = "default-user"
      )
    )
  }

  @Test
  fun `fake with logging enabled`() {
    client.enableLogging = true

    client.logEvent(
      description = "description",
      target = "the-database-001",
    )
    assertThat(logCollector.takeMessage(FakeAuditClient::class))
      .isEqualTo("Audit Event Logged [event=FakeAuditEvent(eventSource=test-app, eventTarget=the-database-001, timestampSent=2147483647, applicationName=test-app, approverLDAP=default-user, automatedChange=false, description=description, richDescription=null, environment=testing, detailURL=null, region=us-west-2, requestorLDAP=default-user)]")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(AppNameModule("test-app"))
      install(MiskWebModule(WebConfig(0)))
      install(AccessControlModule())
      install(MiskTestingServiceModule())
      install(DeploymentModule(TESTING))
      install(LogCollectorModule())

      install(FakeAuditClientModule())
    }
  }
}
