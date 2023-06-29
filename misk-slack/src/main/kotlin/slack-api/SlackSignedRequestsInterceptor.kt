package `slack-api`

import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.reflect.full.findAnnotation
import misk.Action
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import okio.ByteString.Companion.decodeHex
import okio.HashingSink
import okio.blackholeSink
import okio.buffer

@Singleton
class SlackSignedRequestsInterceptor @Inject constructor(
  private val clock: Clock,
  slackConfig: SlackConfig,
) : NetworkInterceptor {
  private val signingSecret = slackConfig.signing_secret.value.decodeHex()

  override fun intercept(chain: NetworkChain) {
    //authorize slack request
    //https://api.slack.com/authentication/verifying-requests-from-slack
    val timestampString = chain.httpCall.requestHeaders["X-Slack-Request-Timestamp"]!!

    //protect against replay attacks
    val epochTime = clock.millis()
    val timestamp = timestampString.toLong() * 1000L
    if (abs(epochTime - timestamp) > 5_000L) {
      throw BadRequestException("Request timestamp is stale")
    }

    val body = chain.httpCall.takeRequestBody()!!

    val slackDigester = HashingSink.hmacSha256(blackholeSink(), signingSecret)
    slackDigester.buffer().use {
      it.writeUtf8("v0=")
      it.writeUtf8(timestampString)
      it.writeUtf8(":")
      it.writeAll(body.peek())
    }

    chain.httpCall.putRequestBody(body)

    val actualSignature = "v0=${slackDigester.hash.hex()}"

    val expectedSignature = chain.httpCall.requestHeaders["X-Slack-Signature"]
    if (actualSignature != expectedSignature) {
      throw UnauthorizedException("Slack headers could not be authenticated!")
    }

    chain.proceed(chain.httpCall)
  }

  @Singleton
  class Factory @Inject constructor(
    private val slackSignedRequestsInterceptor: SlackSignedRequestsInterceptor,
  ) : NetworkInterceptor.Factory {
    override fun create(action: Action): NetworkInterceptor? {
      return when {
        action.function.findAnnotation<SlackSignedRequestsOnly>() != null ->
          slackSignedRequestsInterceptor
        else -> null
      }
    }
  }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class SlackSignedRequestsOnly
