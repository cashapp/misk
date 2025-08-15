package misk.mcp.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json

/**
 * Json serialization instance configured for the misk-mcp module.
 * Its used to deserialize incoming MCP Requests and serialize outgoing
 * MCP Responses.
 */
@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal val McpJson: Json by lazy {
  Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    classDiscriminatorMode = ClassDiscriminatorMode.NONE
    explicitNulls = false
  }
}

