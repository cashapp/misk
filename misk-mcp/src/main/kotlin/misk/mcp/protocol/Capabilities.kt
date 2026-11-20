package misk.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ClientCapabilities
@JvmOverloads
constructor(
  val sampling: Sampling? = null,
  val roots: Roots? = null,
  val elicitation: Elicitation? = null,
  val experimental: JsonObject? = null,
  val extensions: Map<String, JsonObject>? = null,
) {
  @Serializable
  data class Sampling @JvmOverloads constructor(val context: JsonObject? = null, val tools: JsonObject? = null)

  @Serializable data class Roots @JvmOverloads constructor(val listChanged: Boolean? = null)

  /** An elicitation capability that declares neither mode supports `form` only. */
  @Serializable
  data class Elicitation @JvmOverloads constructor(val form: JsonObject? = null, val url: JsonObject? = null) {
    val supportsForm: Boolean
      get() = form != null || url == null

    val supportsUrl: Boolean
      get() = url != null
  }
}

@Serializable
data class ServerCapabilities
@JvmOverloads
constructor(
  val tools: Tools? = null,
  val resources: Resources? = null,
  val prompts: Prompts? = null,
  val logging: JsonObject? = null,
  val completions: JsonObject? = null,
  val experimental: JsonObject? = null,
  val extensions: Map<String, JsonObject>? = null,
) {
  @Serializable data class Tools @JvmOverloads constructor(val listChanged: Boolean? = null)

  @Serializable
  data class Resources @JvmOverloads constructor(val subscribe: Boolean? = null, val listChanged: Boolean? = null)

  @Serializable data class Prompts @JvmOverloads constructor(val listChanged: Boolean? = null)
}
