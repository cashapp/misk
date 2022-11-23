package misk

import com.google.common.util.concurrent.AbstractIdleService
import misk.inject.toKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ServiceModuleTest {
  class Service1 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class Service2 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  class Service3 : AbstractIdleService() {
    override fun startUp() {}
    override fun shutDown() {}
  }

  @Test fun conditionalDependsOn() {
    with(
      ServiceModule<Service1>()
        .dependsOn<Service2>(condition = false)
        .dependsOn<Service3>(condition = true)
    ) {
      assertThat(this.dependsOn).containsExactly(Service3::class.toKey())
      assertThat(this.enhancedBy).isEmpty()
      assertThat(this.enhances).isNull()
      assertThat(this.key).isEqualTo(Service1::class.toKey())
    }
  }
}
