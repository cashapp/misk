package misk.mcp.internal

import com.google.crypto.tink.Mac
import java.security.GeneralSecurityException
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import misk.mcp.McpJson
import misk.mcp.protocol.McpException
import misk.mcp.protocol.RPCError

/**
 * Signs and verifies the `requestState` of input-required results. A client must echo the state unchanged, so it can
 * only replay it, and only on the same request by the same principal (the [binding]) before it expires.
 */
internal class RequestStateCodec(private val mac: Mac, private val clock: Clock) {
  fun seal(state: JsonElement, binding: String): String {
    val payload =
      buildJsonObject {
          put(EXPIRES_AT, clock.millis() + TTL_MS)
          put(BINDING, binding)
          put(STATE, state)
        }
        .toString()
        .toByteArray(Charsets.UTF_8)
    return "${encoder.encodeToString(payload)}.${encoder.encodeToString(mac.computeMac(payload))}"
  }

  fun open(token: String, binding: String): JsonElement {
    val parts = token.split('.')
    if (parts.size != 2) throw invalid("is malformed")
    val (payload, tag) =
      try {
        decoder.decode(parts[0]) to decoder.decode(parts[1])
      } catch (e: IllegalArgumentException) {
        throw invalid("is malformed")
      }
    try {
      mac.verifyMac(tag, payload)
    } catch (e: GeneralSecurityException) {
      throw invalid("has an invalid signature")
    }
    val fields =
      try {
        McpJson.parseToJsonElement(String(payload, Charsets.UTF_8)).jsonObject
      } catch (e: SerializationException) {
        throw invalid("is malformed")
      }
    if (fields.getValue(EXPIRES_AT).jsonPrimitive.long < clock.millis()) throw invalid("has expired")
    if (fields.getValue(BINDING).jsonPrimitive.content != binding) throw invalid("belongs to a different request")
    return fields.getValue(STATE)
  }

  private fun invalid(reason: String) = McpException(RPCError.ErrorCode.INVALID_PARAMS, "requestState $reason")

  companion object {
    val TTL_MS = 10.minutes.inWholeMilliseconds

    private const val EXPIRES_AT = "exp"
    private const val BINDING = "bind"
    private const val STATE = "state"

    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
  }
}
