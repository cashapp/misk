package misk.mcp.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol.HTTP_1_1

fun OkHttpClient.asKtorClient(): HttpClient = HttpClient(OkHttp) {

  engine {
    preconfigured = this@asKtorClient.newBuilder().protocols(listOf(HTTP_1_1)).build()
  }

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
      },
    )
  }

  install(SSE)

  install(Logging) {
    logger = Logger.SIMPLE
    level = LogLevel.INFO
  }

  expectSuccess = false
}

fun HttpClient.asMcpStreamableHttpTransport(baseUrl: HttpUrl, path: String = "/mcp") =
  StreamableHttpClientTransport(this, baseUrl.newBuilder().encodedPath(path).build().toString())

suspend fun StreamableHttpClientTransport.asMcpClient(implementationName: String, version: String) =
  Client(Implementation(name = implementationName, version = version)).also {
    it.connect(this)
  }

suspend fun OkHttpClient.asMcpClient(
  baseUrl: HttpUrl,
  path: String = "/mcp",
  implementationName: String = "misk-mcp-test",
  version: String = "1.0"
) = asKtorClient()
  .asMcpStreamableHttpTransport(baseUrl, path)
  .asMcpClient(implementationName, version)
