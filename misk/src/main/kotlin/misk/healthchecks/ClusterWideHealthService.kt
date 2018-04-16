package misk.healthchecks

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import misk.eventrouter.EventRouter
import misk.eventrouter.KubernetesConfig
import misk.eventrouter.Listener
import misk.eventrouter.Subscription
import misk.healthchecks.ClusterWideHealthService.Companion.PERIOD
import misk.healthchecks.ClusterWideHealthService.Companion.TOPIC
import misk.logging.getLogger
import misk.web.ConnectWebSocket
import misk.web.actions.WebAction
import misk.web.actions.WebSocket
import misk.web.actions.WebSocketListener
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<ClusterWideHealthService>()

/**
 * ClusterWideHealthService publishes its health information on [TOPIC] every
 * [PERIOD]. If it does not receive a health status from a host within [PERIOD] * 2,
 * it will remove it.
 */
@Singleton
class ClusterWideHealthService : AbstractIdleService(), WebAction {
  @Inject lateinit var kubernetesConfig: KubernetesConfig
  @Inject lateinit var eventRouter: EventRouter
  @Inject lateinit var services: List<Service>
  @Inject lateinit var healthChecks: List<HealthCheck>
  @Inject lateinit var moshi: Moshi

  @Inject
  @ForClusterWideHealthService
  lateinit var scheduledExecutorService: ScheduledExecutorService

  private val healthEventJsonAdapter get() = moshi.adapter(HealthEvent::class.java)
  private val healthEventsAdapter: JsonAdapter<Map<String, HealthEvent>>
    get() = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, HealthEvent::class.java))
  private var subscribers = CopyOnWriteArrayList<WebSocket>()
  private var subscription: Subscription<String>? = null

  val healthCheckCache: Cache<String, HealthEvent> = CacheBuilder.newBuilder()
      .expireAfterWrite(PERIOD.seconds * 2, TimeUnit.SECONDS)
      .removalListener<String, HealthEvent> { clusterHealthChanged() }
      .build<String, HealthEvent>()

  @ConnectWebSocket("/health")
  fun health(webSocket: WebSocket): WebSocketListener {
    subscribers.add(webSocket)
    sendClusterHealth(webSocket)
    return object : WebSocketListener() {
      override fun onClosed(webSocket: WebSocket, code: Int, reason: String?) {
        subscribers.remove(webSocket)
      }

      override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
        subscribers.remove(webSocket)
      }

      override fun onFailure(webSocket: WebSocket, t: Throwable) {
        subscribers.remove(webSocket)
      }
    }
  }

  override fun startUp() {
    logger.info { "starting up ClusterWideHealthService" }
    scheduledExecutorService.scheduleAtFixedRate({
      val json = healthEventJsonAdapter.toJson(
          HealthEvent(
              kubernetesConfig.my_pod_name,
              healthChecks.map {
                val status = it.status()
                val name = it::class.java.canonicalName
                HealthStatusWithName(name, status.isHealthy, status.messages)
              }
          )
      )
      eventRouter.getTopic<String>(TOPIC).publish(json)
    }, 0, PERIOD.seconds, TimeUnit.SECONDS)
    subscribe()
  }

  override fun shutDown() {
    logger.info { "shutting down ClusterWideHealthService" }
    subscription?.cancel()
    scheduledExecutorService.shutdown()
  }

  private fun clusterHealthChanged() {
    for (subscriber in subscribers) {
      subscriber.send(healthEventsAdapter.toJson(healthCheckCache.asMap().toSortedMap()))
    }
  }

  private fun sendClusterHealth(webSocket: WebSocket) {
    webSocket.send(healthEventsAdapter.toJson(healthCheckCache.asMap().toSortedMap()))
  }

  private fun subscribe() {
    val topic = eventRouter.getTopic<String>(TOPIC)

    val listener = object : Listener<String> {
      override fun onEvent(subscription: Subscription<String>, event: String) {
        val healthEvent = healthEventJsonAdapter.fromJson(event)!!
        healthCheckCache.put(healthEvent.hostname, healthEvent)
        clusterHealthChanged()
      }

      override fun onOpen(subscription: Subscription<String>) = Unit

      override fun onClose(subscription: Subscription<String>) {
        // Delay 3 seconds (arbitrary) before trying to reconnect
        scheduledExecutorService.schedule({ subscribe() }, 3, TimeUnit.SECONDS)
      }
    }
    subscription = topic.subscribe(listener)
  }

  companion object {
    val PERIOD: Duration = Duration.ofSeconds(3)
    const val TOPIC = "cluster_wide_health_service"
  }

  data class HealthEvent(
    val hostname: String,
    val healthStatuses: List<HealthStatusWithName>
  )

  data class HealthStatusWithName(
    val name: String,
    val isHealthy: Boolean,
    val messages: List<String>
  )
}
