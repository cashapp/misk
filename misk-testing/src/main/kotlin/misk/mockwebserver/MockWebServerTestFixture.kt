package misk.mockwebserver

import java.util.concurrent.LinkedBlockingQueue
import misk.testing.TestFixture
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest

/** A resettable TestFixture around a MockWebServer which can be used in Misk tests with injector reuse. */
class MockWebServerTestFixture(private val mockServer: MockWebServer) : TestFixture {

  private lateinit var wrappedDispatcher: QueueStateHolderDispatcher

  val requestCount: Int
    get() = this.wrappedDispatcher.requestCount()

  init {
    resetDispatcher()
  }

  fun takeRequest(): RecordedRequest = wrappedDispatcher.takeRequest()

  fun enqueue(response: MockResponse) {
    this.wrappedDispatcher.enqueue(response)
  }

  fun url(): okhttp3.HttpUrl {
    mockServer.start()
    return mockServer.url("/")
  }

  override fun reset() {
    resetDispatcher()
  }

  private fun resetDispatcher() {
    this.wrappedDispatcher = QueueStateHolderDispatcher()
    mockServer.dispatcher = this.wrappedDispatcher
  }
}

internal class QueueStateHolderDispatcher : Dispatcher() {

  private val delegate = QueueDispatcher()
  private var requestCount: Int = 0
  private val requestQueue = LinkedBlockingQueue<RecordedRequest>()

  fun requestCount(): Int = requestCount

  override fun dispatch(request: RecordedRequest): MockResponse {
    requestCount = requestCount + 1
    requestQueue.add(request)
    return delegate.dispatch(request)
  }

  fun takeRequest(): RecordedRequest = requestQueue.take()

  fun enqueue(response: MockResponse) {
    delegate.enqueue(response)
  }
}
