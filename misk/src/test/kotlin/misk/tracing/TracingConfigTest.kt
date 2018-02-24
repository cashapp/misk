package misk.tracing

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.util.Modules
import io.opentracing.Tracer
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.inject.getInstance
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
      ConfigModule.create<TestTracingConfig>("test_tracing_app", config),
      TracingModule(config.tracing),
      EnvironmentModule(defaultEnv)
  )

  @Inject
  private lateinit var tracingTestConfig: TestTracingConfig

  @Inject
  private lateinit var tracer: Tracer

  @Test
  fun tracerProperlyInjected() {
    assertThat(config.tracing.tracer).isEqualTo(tracingTestConfig.tracing.tracer)
    assertThat(tracer).isInstanceOf(com.uber.jaeger.Tracer::class.java)
  }

  @Test
  fun failsIfTracerNotFound() {
    val badConfig = MiskConfig.load<TestTracingConfig>(TestTracingConfig::class.java, "test_tracing_app-missing", defaultEnv)
    val module = Modules.combine(
        ConfigModule.create<TestTracingConfig>("test_tracing_app-missing", badConfig),
        TracingModule(badConfig.tracing),
        EnvironmentModule(defaultEnv)
    )
    val exception = assertThrows(CreationException::class.java, {
      Guice.createInjector(module).getInstance<Tracer>()
    })

    assertThat(exception.localizedMessage).contains("No backend for 'missing_tracer' tracer")
  }
}