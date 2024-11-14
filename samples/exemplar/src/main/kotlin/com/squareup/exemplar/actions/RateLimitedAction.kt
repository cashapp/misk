package com.squareup.exemplar.actions

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.exceptions.TooManyRequestsException
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.mediatype.MediaTypes
import wisp.ratelimiting.RateLimitConfiguration
import wisp.ratelimiting.RateLimiter
import java.time.Duration
import kotlin.random.Random

@Singleton
class RateLimitedAction @Inject constructor(
  private val rateLimiter: RateLimiter
) {
  @Unauthenticated
  @Get("/expensive-rate-limited-action")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun rateLimitedExample(): RateLimitedExampleResponse {
    val sourceIp = "192.168.1.1"
    val result = rateLimiter.withToken(sourceIp, ExampleRateLimitConfiguration) {
      RateLimitedExampleResponse(Random.nextLong())
    }

    val consumptionData = result.consumptionData
    return if (consumptionData.didConsume) {
      result.result!!
    } else {
      throw TooManyRequestsException()
    }
  }
}

data class RateLimitedExampleResponse(val number: Long)

object ExampleRateLimitConfiguration : RateLimitConfiguration {
  override val capacity = 10L
  override val name = "ExpensiveRateLimitedAction"
  override val refillAmount = 10L
  override val refillPeriod: Duration = Duration.ofMinutes(1L)
  override val version = 0L // increment the version when updating the configuration
}
