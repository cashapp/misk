package misk.tracing

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.util.Modules
import io.opentracing.Tracer
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest
class TracingConfigTest {
  val defaultEnv = Environment.TESTING
  val config = MiskConfig.load<TestTracingConfig>("test_tracing_app", defaultEnv)
  @MiskTestModule
  val module = Modules.combine(
      ConfigModule.create("test_tracing_app", config),
      TracingModule(config.tracing),
      EnvironmentModule(defaultEnv)
  )

  @Inject
  private lateinit var tracer: Tracer

  @Test
  fun tracerProperlyInjected() {
    assertThat(tracer).isInstanceOf(com.uber.jaeger.Tracer::class.java)
  }

  @Test
  fun multipleTracerConfigs() {
    val config = MiskConfig.load<TestTracingConfig>(
        TestTracingConfig::class.java, "multiple-tracers", defaultEnv)

    val exception = assertThrows(CreationException::class.java, {
      Guice.createInjector(ConfigModule.create("test_app", config), TracingModule(config.tracing))
    })

    assertThat(exception.cause).isInstanceOf(IllegalStateException::class.java)
    assertThat(exception.localizedMessage).contains("More than one tracer has been configured." +
        " Please remove one.")
  }
}
