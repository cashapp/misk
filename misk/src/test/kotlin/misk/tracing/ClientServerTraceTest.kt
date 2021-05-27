package misk.tracing

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.name.Names
import helpers.protos.Dinosaur
import io.opentracing.Tracer
import io.opentracing.mock.MockSpan
import io.opentracing.mock.MockTracer
import javax.inject.Inject
import javax.inject.Singleton
import misk.MiskTestingServiceModule
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientsConfig
import misk.client.HttpClientsConfigModule
import misk.client.TypedHttpClientModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.inject.keyOf
import misk.testing.ConcurrentMockTracer
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.testing.MockTracingBackendModule
import misk.web.Post
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

@MiskTest(startService = true)
internal class ClientServerTraceTest {
  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  @Inject
  private lateinit var serverTracer: ConcurrentMockTracer

  @Inject
  private lateinit var serverInjector: Injector

  private lateinit var clientInjector: Injector

  private val dinosaurRequest = Dinosaur.Builder().name("trex").build()

  @BeforeEach
  fun createClient() {
    clientInjector = Guice.createInjector(
      MockTracingBackendModule(), MiskTestingServiceModule(), ClientModule(jetty)
    )
  }

  @Test
  fun embedsTrace() {
    val client = clientInjector.getInstance<ReturnADinosaur>(Names.named("dinosaur"))
    client.getDinosaur(dinosaurRequest).execute()

    val serverSpan = serverTracer.take("http.action")
    // Parent ID of 0 means there is no parent span
    assertThat(serverSpan.parentId()).isGreaterThan(0)

    val clientTracer = clientInjector.getInstance(MockTracer::class.java)
    // Two spans here because one is created at the app level and another at the network interceptor
    // level.
    assertThat(clientTracer.finishedSpans().size).isEqualTo(2)
    val clientSpan =
      clientTracer.finishedSpans().find { it.context().spanId() == serverSpan.parentId() }

    assertThat(clientSpan).isNotNull()
  }

  @Test
  fun noTracerNoTracesOrFailures() {
    val clientInjector = Guice.createInjector(MiskTestingServiceModule(), ClientModule(jetty))
    val client = clientInjector.getInstance<ReturnADinosaur>(Names.named("dinosaur"))

    client.getDinosaur(dinosaurRequest).execute()

    val span = serverTracer.take("http.action")
    assertThat(span.parentId()).isEqualTo(0)

    assertThat(clientInjector.allBindings.filter { it.key == keyOf<Tracer>() }).isEmpty()
  }

  @Test
  fun traceHopsFromClientToServerToServer() {
    val childServerInjector = serverInjector.createChildInjector(ClientModule(jetty))
    RoarLikeDinosaurAction.returnADinosaur =
      childServerInjector.getInstance<ReturnADinosaur>(Names.named("dinosaur"))

    val client = clientInjector.getInstance<RoarLikeDinosaur>(Names.named("roar"))
    client.doRoar(dinosaurRequest).execute()

    // Expect 4 spans on the server.
    val serverSpans = listOf(
      serverTracer.take("http.action"),
      serverTracer.take("POST"),
      serverTracer.take("POST"),
      serverTracer.take("http.action")
    )

    val spanIds = serverSpans.map { it.context().spanId() }.toSet()
    val traceId = serverSpans[0].context().traceId()

    var initialServerSpan: MockSpan? = null
    for (span in serverSpans) {
      // Parent ID of 0 means there is no parent span
      assertThat(span.parentId()).isGreaterThan(0)

      // Assert trace IDs are all the same (i.e. no new traces, new spans added as children)
      assertThat(span.context().traceId()).isEqualTo(traceId)

      if (!spanIds.contains(span.parentId())) initialServerSpan = span
    }

    assertThat(initialServerSpan).isNotNull()

    val clientTracer = clientInjector.getInstance(Tracer::class.java) as MockTracer
    // Two spans here because one is created at the app level and another at the network interceptor
    // level.
    assertThat(clientTracer.finishedSpans().size).isEqualTo(2)
    val clientSpan =
      clientTracer.finishedSpans()
        .find { it.context().spanId() == initialServerSpan!!.parentId() }

    assertThat(clientSpan).isNotNull()
  }

  interface ReturnADinosaur {
    @POST("/cooldinos")
    fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
  }

  class ReturnADinosaurAction @Inject constructor() : WebAction {
    @Post("/cooldinos")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun getDinosaur(@RequestBody request: Dinosaur):
      Dinosaur = request.newBuilder().name("super${request.name}").build()
  }

  interface RoarLikeDinosaur {
    @POST("/roar")
    fun doRoar(@Body request: Dinosaur): Call<Dinosaur>
  }

  @Singleton
  class RoarLikeDinosaurAction @Inject constructor() : WebAction {
    // NOTE(nb): hackily pass in a client because I can't Guice well enough to figure out how to
    // inject this and I'm exhausted from trying to make it work.
    companion object {
      var returnADinosaur: ReturnADinosaur? = null
    }

    @Post("/roar")
    @RequestContentType(MediaTypes.APPLICATION_JSON)
    @ResponseContentType(MediaTypes.APPLICATION_JSON)
    fun doRoar(@RequestBody request: Dinosaur):
      Dinosaur = returnADinosaur!!.getDinosaur(request).execute().body()!!
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MockTracingBackendModule())
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<ReturnADinosaurAction>())
      install(WebActionModule.create<RoarLikeDinosaurAction>())
    }
  }

  class ClientModule(val jetty: JettyService) : KAbstractModule() {
    override fun configure() {
      install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur", Names.named("dinosaur")))
      install(TypedHttpClientModule.create<RoarLikeDinosaur>("roar", Names.named("roar")))
      install(
        HttpClientsConfigModule(
          HttpClientsConfig(
            endpoints = mapOf(
              "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString()),
              "roar" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
            )
          )
        )
      )
    }
  }
}
