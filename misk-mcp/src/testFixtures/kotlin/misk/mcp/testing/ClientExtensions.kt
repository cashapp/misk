package misk.mcp.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.EmptyJsonObject
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol.HTTP_1_1

fun OkHttpClient.asKtorClient(webSocket: Boolean = true): HttpClient = HttpClient(OkHttp) {

  engine {
    preconfigured = this@asKtorClient.newBuilder().protocols(listOf(HTTP_1_1)).build()
  }

  if (webSocket) {
    install(WebSockets)
  } else {
    install(SSE)
    install(ContentNegotiation) {
      json(
        Json {
          ignoreUnknownKeys = true
          explicitNulls = false
          isLenient = true
        },
      )
    }
  }

  install(Logging) {
    logger = Logger.SIMPLE
    level = LogLevel.INFO
    format = LoggingFormat.OkHttp
  }

  expectSuccess = false
}

fun HttpUrl.normalizeWebSocketUrlString(): String {
  return when (scheme) {
    "http" -> "ws" + toString().removePrefix("http")
    "https" -> "wss" + toString().removePrefix("https")
    else -> toString()
  }
}

fun HttpClient.asMcpWebSocketTransport(baseUrl: HttpUrl, path: String = "/mcp"): WebSocketClientTransport {
  val url = baseUrl.newBuilder().encodedPath(path).build().normalizeWebSocketUrlString()
  return WebSocketClientTransport(this, url)
}

fun HttpClient.asMcpStreamableHttpTransport(baseUrl: HttpUrl, path: String = "/mcp"): StreamableHttpClientTransport {
  val url = baseUrl.newBuilder().encodedPath(path).build().toString()
  return StreamableHttpClientTransport(this, url)
}

suspend fun AbstractTransport.asMcpClient(
  implementationName: String,
  version: String,
  options: ClientOptions = ClientOptions()
) =
  Client(Implementation(name = implementationName, version = version), options).also {
    it.connect(this)
  }

suspend fun OkHttpClient.asMcpStreamableHttpClient(
  baseUrl: HttpUrl,
  path: String = "/mcp",
  implementationName: String = "misk-mcp-test",
  version: String = "1.0",
  enforceStrictCapabilities: Boolean = true,
  supportsExperimental: Boolean = true,
  supportsSampling: Boolean = true,
  supportsElicitation: Boolean = false,
  supportsRoots: Boolean = false,
) = asKtorClient(false)
  .asMcpStreamableHttpTransport(baseUrl, path)
  .asMcpClient(
    implementationName, version, clientOptionsFor(
      enforceStrictCapabilities,
      supportsExperimental,
      supportsSampling,
      supportsElicitation,
      supportsRoots,
    )
  )

suspend fun OkHttpClient.asMcpWebSocketClient(
  baseUrl: HttpUrl,
  path: String = "/mcp",
  implementationName: String = "misk-mcp-test",
  version: String = "1.0",
  enforceStrictCapabilities: Boolean = true,
  supportsExperimental: Boolean = true,
  supportsSampling: Boolean = true,
  supportsElicitation: Boolean = false,
  supportsRoots: Boolean = false,
) = asKtorClient()
  .asMcpWebSocketTransport(baseUrl, path)
  .asMcpClient(
    implementationName, version, clientOptionsFor(
      enforceStrictCapabilities,
      supportsExperimental,
      supportsSampling,
      supportsElicitation,
      supportsRoots,
    )
  )

fun clientOptionsFor(
  enforceStrictCapabilities: Boolean = true,
  supportsExperimental: Boolean = true,
  supportsSampling: Boolean = true,
  supportsElicitation: Boolean = false,
  supportsRoots: Boolean = false,
) = ClientOptions(
  enforceStrictCapabilities = enforceStrictCapabilities,
  capabilities = ClientCapabilities(
    experimental = if (supportsExperimental) EmptyJsonObject else null,
    sampling = if (supportsSampling) EmptyJsonObject else null,
    elicitation = if (supportsElicitation) EmptyJsonObject else null,
    roots = if (supportsRoots) ClientCapabilities.Roots(false) else null,
  )
)