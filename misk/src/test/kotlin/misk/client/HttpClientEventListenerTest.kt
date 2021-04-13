package misk.client

import com.google.inject.Guice
import helpers.protos.Dinosaur
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.inject.getInstance
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.jetty.JettyService
import okhttp3.Call
import okhttp3.EventListener
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@MiskTest(startService = true)
internal class HttpClientEventListenerTest {

  @MiskTestModule
  val module = TestModule()

  @Inject
  private lateinit var jetty: JettyService

  @Inject
  private lateinit var testListener: TestEventListener

  private lateinit var client: ReturnADinosaur

  @BeforeEach
  fun createClient() {
    val clientInjector = Guice.createInjector(ClientModule(jetty, testListener))
    client = clientInjector.getInstance()
  }

  @Test
  fun eventListener() {
    assertThat(testListener.started()).isFalse()
    val response = client.getDinosaur(Dinosaur.Builder().name("trex").build()).execute()
    Assertions.assertThat(response.code()).isEqualTo(200)
    assertThat(response.body()).isNotNull()
    assertThat(testListener.started()).isTrue()
  }

  class ClientModule(val jetty: JettyService, val eventListener: TestEventListener) :
    KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(DinoClientModule(jetty))
      bind<EventListener.Factory>().to<TestEventListenerFactory>()
      bind<TestEventListener>().toInstance(eventListener)
    }
  }

  class TestEventListenerFactory @Inject constructor(private val listener: TestEventListener) :
    EventListener.Factory {
    override fun create(call: Call): EventListener {
      return listener
    }
  }

  @Singleton
  class TestEventListener @Inject constructor() : EventListener() {
    private var started = false
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
      started = true
    }

    fun started(): Boolean {
      return started
    }
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(MiskTestingServiceModule())
      install(WebServerTestingModule())
      install(WebActionModule.create<ReturnADinosaurAction>())
    }
  }
}
