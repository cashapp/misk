package misk.tracing

import com.google.inject.CreationException
import com.google.inject.Guice
import com.google.inject.util.Modules
import io.opentracing.Tracer
import misk.MiskModule
import misk.config.ConfigModule
import misk.config.MiskConfig
import misk.environment.Environment
import misk.environment.EnvironmentModule
import misk.resources.ResourceLoaderModule
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.assertThrows
import misk.web.WebModule
import org.assertj.core.api.Assertions.assertThat
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
      EnvironmentModule(defaultEnv),
//    @TODO(jwilson swankjesse) https://github.com/square/misk/issues/272
      ResourceLoaderModule(),
      WebModule(),
      MiskModule()

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

    val exception = assertThrows<CreationException> {
      Guice.createInjector(ConfigModule.create("test_app", config), TracingModule(config.tracing))
    }

    assertThat(exception.cause).isInstanceOf(IllegalStateException::class.java)
    assertThat(exception.localizedMessage).contains("More than one tracer has been configured." +
        " Please remove one.")
  }
}
