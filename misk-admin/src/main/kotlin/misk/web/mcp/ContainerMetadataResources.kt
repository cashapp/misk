@file:OptIn(ExperimentalMiskApi::class)

package misk.web.mcp

import com.google.inject.Provider
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.annotation.ExperimentalMiskApi
import misk.mcp.McpResource
import misk.web.metadata.Metadata

private fun metadataResource(
  allMetadata: Map<String, Provider<Metadata>>,
  metadataId: String,
  resourceName: String,
  resourceDescription: String,
): ReadResourceResult {
  val uri = "admin://metadata/$metadataId"
  val provider = allMetadata[metadataId]
  val text =
    if (provider != null) {
      try {
        provider.get().prettyPrint
      } catch (e: Exception) {
        "Error loading $metadataId metadata: ${e.message}"
      }
    } else {
      "Metadata provider \"$metadataId\" is not registered. Available: ${allMetadata.keys.sorted().joinToString(", ")}"
    }
  return ReadResourceResult(contents = listOf(TextResourceContents(uri = uri, text = text, mimeType = "text/plain")))
}

@Singleton
class ConfigMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/config"
  override val name = "Application Config"
  override val description = "Application configuration YAML including effective config and raw YAML files"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) =
    metadataResource(allMetadata, "config", name, description)
}

@Singleton
class JvmMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/jvm"
  override val name = "JVM Runtime"
  override val description = "JVM runtime information including VM version, uptime, system properties, and classpath"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) = metadataResource(allMetadata, "jvm", name, description)
}

@Singleton
class WebActionsMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/web-actions"
  override val name = "Web Actions"
  override val description =
    "All registered web action endpoints with paths, HTTP methods, authentication, and request/response types"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) =
    metadataResource(allMetadata, "web-actions", name, description)
}

@Singleton
class GuiceMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/guice"
  override val name = "Guice Bindings"
  override val description = "Guice dependency injection bindings including types, scopes, providers, and annotations"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) = metadataResource(allMetadata, "guice", name, description)
}

@Singleton
class ServiceGraphMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/service-graph"
  override val name = "Service Graph"
  override val description = "Guava service dependency graph with ASCII visualization and dependency mapping"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) =
    metadataResource(allMetadata, "service-graph", name, description)
}

@Singleton
class DatabaseMcpResource
@Inject
constructor(private val allMetadata: Map<String, @JvmSuppressWildcards Provider<Metadata>>) : McpResource {
  override val uri = "admin://metadata/database-hibernate"
  override val name = "Database"
  override val description =
    "Database schema metadata including Hibernate MySQL JDBC configurations and query definitions"
  override val mimeType = "text/plain"

  override suspend fun handler(request: ReadResourceRequest) =
    metadataResource(allMetadata, "database-hibernate", name, description)
}
