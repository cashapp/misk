package misk.audit

import com.google.inject.Guice
import com.google.inject.Stage
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.web.WebServerTestingModule
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class FakeAuditRequestCompletionModuleTest {
  @Test
  fun `completion module rejects a non-fake audit client at injector startup`() {
    assertThatThrownBy {
        Guice.createInjector(
          Stage.PRODUCTION,
          object : KAbstractModule() {
            override fun configure() {
              install(WebServerTestingModule())
              install(MiskTestingServiceModule())
              install(NoOpAuditClientModule())
              install(FakeAuditRequestCompletionModule())
            }
          },
        )
      }
      .hasStackTraceContaining("requires FakeAuditClientModule")
  }
}
