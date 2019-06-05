package misk.web.interceptors

import misk.Action
import misk.logging.getLogger
import misk.random.ThreadLocalRandom
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.WebConfig
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<RebalancingInterceptor>()

/**
 * When we're deploying and redeploying our pods, we want to make sure that clients rebalance onto
 * the new pods. This randomly closes connections so they will be recreated, naturally balancing
 * connections across pods.
 */
class RebalancingInterceptor @Inject constructor(
  private val random: ThreadLocalRandom,
  private val probability: Double
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain) {
    if (random.current().nextDouble() < probability) {
      chain.request.setResponseHeader("Connection", "close")
    }

    chain.proceed(chain.request)
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