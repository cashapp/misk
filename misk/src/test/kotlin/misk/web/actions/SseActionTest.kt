package misk.web.actions

import com.squareup.moshi.Moshi
import com.squareup.wire.MessageSink
import jakarta.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.test.DefaultAsserter.fail
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import misk.MiskTestingServiceModule
import misk.inject.KAbstractModule
import misk.moshi.adapter
import misk.security.authz.Unauthenticated
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.web.Get
import misk.web.RequestHeader
import misk.web.ResponseContentType
import misk.web.WebActionModule
import misk.web.WebServerTestingModule
import misk.web.actions.ResponseContentTypeTest.HelloAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import misk.web.sse.LAST_EVENT_ID_HEADER
import misk.web.sse.ServerSentEvent
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@MiskTest(startService = true)
class SseActionTest {

  @MiskTestModule val module = TestModule()

  @Inject private lateinit var moshi: Moshi

  @Inject private lateinit var jettyService: JettyService

  private val httpClient = OkHttpClient()

  @ParameterizedTest(name = "sse events are handled property from ''{0}''")
  @ValueSource(strings = ["/sse", "/blocking_sse"])
  fun `sse events are handled property`(url: String) = runTest {
    val request =
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(url).build())
        .header("Accept", MediaTypes.SERVER_EVENT_STREAM)
        .build()

    val events = suspendCoroutine { cont ->
      EventSources.createFactory(httpClient)
        .newEventSource(
          request,
          object : EventSourceListener() {
            val events = mutableMapOf<String, Feature>()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
              assertNotNull(id)
              val feature = moshi.adapter<Feature>().fromJson(data)
              assertNotNull(feature)
              events.put(id, feature)
            }

            override fun onClosed(eventSource: EventSource) {
              cont.resume(events)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
              fail("SSE connection failed: ${t?.message ?: response?.message}")
            }
          },
        )
    }

    assert(events.size == 10) { "Expected 10 events, but got ${events.size}" }
    for (i in 1..10) {
      val feature = events["$i"]
      assertNotNull(feature) { "Feature with id $i should not be null" }
      assert(feature.name == "Feature $i") { "Expected Feature name 'Feature $i', but got '${feature.name}'" }
      assert(feature.lat == i) { "Expected Feature lat $i, but got '${feature.lat}'" }
      assert(feature.lon == i * 10) { "Expected Feature lon ${i * 10}, but got '${feature.lon}'" }
    }
  }

  @ParameterizedTest(name = "sse events are handled properly when resumed from ''{0}''")
  @ValueSource(strings = ["/sse", "/blocking_sse"])
  fun `sse events are handled properly when resumed`(url: String) = runTest {
    // First connection - receive 5 events then close
    val firstRequest =
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(url).build())
        .header("Accept", MediaTypes.SERVER_EVENT_STREAM)
        .build()

    val (firstEvents, lastEventId) =
      suspendCoroutine { cont ->
        EventSources.createFactory(httpClient)
          .newEventSource(
            firstRequest,
            object : EventSourceListener() {
              val events = mutableMapOf<String, Feature>()
              var lastId: String? = null

              override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                assertNotNull(id)
                lastId = id
                val feature = moshi.adapter<Feature>().fromJson(data)
                assertNotNull(feature)
                events.put(id, feature)

                // Close connection after receiving 5 events
                if (events.size == 5) {
                  eventSource.cancel()
                  cont.resume(Pair(events, lastId))
                }
              }

              override fun onClosed(eventSource: EventSource) {
                // This will be called when we cancel the connection
                if (events.size == 5) {
                  // Already resumed in onEvent
                } else {
                  cont.resume(Pair(events, lastId))
                }
              }

              override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                fail("First SSE connection failed: ${t?.message ?: response?.message}")
              }
            },
          )
      }

    // Verify first 5 events
    assert(firstEvents.size == 5) { "Expected 5 events in first connection, but got ${firstEvents.size}" }
    for (i in 1..5) {
      val feature = firstEvents["$i"]
      assertNotNull(feature) { "Feature with id $i should not be null" }
      assert(feature.name == "Feature $i") { "Expected Feature name 'Feature $i', but got '${feature.name}'" }
      assert(feature.lat == i) { "Expected Feature lat $i, but got '${feature.lat}'" }
      assert(feature.lon == i * 10) { "Expected Feature lon ${i * 10}, but got '${feature.lon}'" }
    }
    assert(lastEventId == "5") { "Expected last event ID to be '5', but got '$lastEventId'" }

    // Second connection - resume from event 5
    val resumeRequest =
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(url).build())
        .header("Accept", MediaTypes.SERVER_EVENT_STREAM)
        .header("Last-Event-ID", lastEventId!!)
        .build()

    val resumedEvents = suspendCoroutine { cont ->
      EventSources.createFactory(httpClient)
        .newEventSource(
          resumeRequest,
          object : EventSourceListener() {
            val events = mutableMapOf<String, Feature>()

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
              assertNotNull(id)
              val feature = moshi.adapter<Feature>().fromJson(data)
              assertNotNull(feature)
              events.put(id, feature)
            }

            override fun onClosed(eventSource: EventSource) {
              cont.resume(events)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
              fail("Resumed SSE connection failed: ${t?.message ?: response?.message}")
            }
          },
        )
    }

    // Verify resumed events (6-10)
    assert(resumedEvents.size == 5) { "Expected 5 events in resumed connection, but got ${resumedEvents.size}" }
    for (i in 6..10) {
      val feature = resumedEvents["$i"]
      assertNotNull(feature) { "Feature with id $i should not be null" }
      assert(feature.name == "Feature $i") { "Expected Feature name 'Feature $i', but got '${feature.name}'" }
      assert(feature.lat == i) { "Expected Feature lat $i, but got '${feature.lat}'" }
      assert(feature.lon == i * 10) { "Expected Feature lon ${i * 10}, but got '${feature.lon}'" }
    }

    // Verify all events were received across both connections
    val allEvents = firstEvents + resumedEvents
    assert(allEvents.size == 10) { "Expected 10 total events, but got ${allEvents.size}" }
  }

  @ParameterizedTest(name = "sse event headers are set correctly from ''{0}''")
  @ValueSource(strings = ["/sse", "/blocking_sse"])
  fun `sse event headers are set correctly`(url: String) = runTest {
    val request =
      Request.Builder()
        .url(jettyService.httpServerUrl.newBuilder().encodedPath(url).build())
        .header("Accept", MediaTypes.SERVER_EVENT_STREAM)
        .build()

    val response = suspendCoroutine { cont ->
      EventSources.createFactory(httpClient)
        .newEventSource(
          request,
          object : EventSourceListener() {

            override fun onOpen(eventSource: EventSource, response: Response) {
              cont.resume(response)
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
              fail("SSE connection failed: ${t?.message ?: response?.message}")
            }
          },
        )
    }
    assertThat(response.code).isEqualTo(200)
    assertThat(response.header("Content-Type")).isEqualTo("text/event-stream")
    assertThat(response.header("Cache-Control")).isEqualTo("no-cache")
    assertThat(response.header("Connection")).isEqualTo("keep-alive")
    assertThat(response.header("X-Accel-Buffering")).isEqualTo("no")
  }

  class TestModule : KAbstractModule() {
    override fun configure() {
      install(WebServerTestingModule())
      install(MiskTestingServiceModule())
      install(WebActionModule.create<HelloAction>())
      install(WebActionModule.create<BlockingSeverSentEventsAction>())
      install(WebActionModule.create<SuspendingSeverSentEventsAction>())
    }
  }
}

class Feature(val name: String, val lat: Int, val lon: Int)

class BlockingSeverSentEventsAction @Inject constructor(private val moshi: Moshi) : WebAction {
  @Suppress("unused")
  @Get("/blocking_sse")
  @ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
  @Unauthenticated
  fun handle(eventSink: MessageSink<ServerSentEvent>, @RequestHeader(LAST_EVENT_ID_HEADER) lastEventId: Int = 0) {
    for (i in lastEventId + 1..10) {
      eventSink.write(
        ServerSentEvent(
          id = "$i",
          event = "data-$i",
          data = moshi.adapter<Feature>().toJson(Feature(name = "Feature $i", lat = i, lon = i * 10)),
        )
      )
      Thread.sleep(10) // Simulate some delay
    }
  }
}

class SuspendingSeverSentEventsAction @Inject constructor(private val moshi: Moshi) : WebAction {
  @Suppress("unused")
  @Get("/sse")
  @ResponseContentType(MediaTypes.SERVER_EVENT_STREAM)
  @Unauthenticated
  suspend fun handle(
    responseChannel: SendChannel<ServerSentEvent>,
    @RequestHeader(LAST_EVENT_ID_HEADER) lastEventId: Int = 0,
  ) {
    for (i in lastEventId + 1..10) {
      responseChannel.send(
        ServerSentEvent(
          id = "$i",
          data = moshi.adapter<Feature>().toJson(Feature(name = "Feature $i", lat = i, lon = i * 10)),
        )
      )
      delay(10) // Simulate some delay
    }
  }
}
