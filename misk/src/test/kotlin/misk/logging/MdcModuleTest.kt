package misk.logging

import jakarta.inject.Inject
import misk.MiskTestingServiceModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@MiskTest(startService = false)
internal class MdcModuleTest {
  @MiskTestModule val module = MiskTestingServiceModule()

  @Inject lateinit var mdc: Mdc

  @Test
  fun mdcIsInstalled() {
    mdc.put("some-key", "some-value")
    assertThat(mdc.get("some-key")).isEqualTo("some-value")

    mdc.clear()
    assertThat(mdc.get("some-key")).isNull()
  }
}
