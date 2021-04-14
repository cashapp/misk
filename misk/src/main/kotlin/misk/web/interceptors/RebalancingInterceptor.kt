package misk.web.interceptors

import misk.Action
import misk.random.ThreadLocalRandom
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.SocketAddress
import misk.web.WebConfig
import wisp.logging.getLogger
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<RebalancingInterceptor>()

/**
 * When we're deploying and redeploying our pods, we want to make sure that clients rebalance onto
 * the new pods. This randomly closes connections so they will be recreated, naturally balancing
 * connections across pods.
 *
 * This does not close Unix domain socket connections. This interceptor is intended to mitigate
 * imbalanced load from long-lived client connections maintained from client apps.
 * Connections over UDS are oriented towards service mesh sidecars that employ sufficient
 * client-side load balancing.
 */
class RebalancingInterceptor @Inject constructor(
  private val random: ThreadLocalRandom,
  private val probability: Double
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    if (chain.httpCall.linkLayerLocalAddress is SocketAddress.Unix) {
      chain.proceed(chain.httpCall)
      return
    }

    if (random.current().nextDouble() < probability) {
      logger.info { "sending 'connection: close' response header for cluster balance" }
      chain.httpCall.setResponseHeader("Connection", "close")
    }

    chain.proceed(chain.httpCall)
  }

  @Singleton
  class Factory @Inject internal constructor(
    private val random: ThreadLocalRandom,
    private val webConfig: WebConfig
  ) : NetworkInterceptor.Factory {
    init {
      val percent = webConfig.close_connection_percent
      logger.info {
        "sending connection: close to ${String.format("%.2f", percent)}% of all responses"
      }
    }

    override fun create(action: Action): NetworkInterceptor? {
      val percent = webConfig.close_connection_percent
      require(percent in 0.0..100.0)

      if (percent == 0.0) return null

      return RebalancingInterceptor(random, percent / 100)
    }
  }
}
