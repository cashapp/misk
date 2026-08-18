package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import misk.annotation.ExperimentalMiskApi

/**
 * Abstraction for a resource template in the Model Context Protocol (MCP) specification.
 *
 * Resource templates are parameterized resources that use RFC 6570 URI templates
 * (e.g., `schema://database/{tableName}`) to match multiple URIs dynamically.
 * When a client reads a URI matching the template, the server extracts the variable
 * values and passes them to the handler.
 *
 * ## Implementation Requirements
 *
 * Implementations must:
 * - Provide a [uriTemplate] using RFC 6570 Level 1 syntax (simple `{variableName}` expressions)
 * - Include a human-readable [name] for the resource template
 * - Include a [description] explaining what resources this template serves
 * - Optionally specify a [mimeType] (defaults to "text/html")
 * - Implement the [handler] function to return content based on the request and extracted variables
 *
 * ## Registration
 *
 * Resource templates should be registered with an MCP server using [McpResourceTemplateModule]:
 * ```kotlin
 * // In your Guice module configuration
 * install(McpResourceTemplateModule.create<MyResourceTemplate>())
 * ```
 *
 * ## Example Implementation
 *
 * ```kotlin
 * @Singleton
 * class TableSchemaResource @Inject constructor(
 *   private val schemaService: SchemaService
 * ) : McpResourceTemplate {
 *   override val uriTemplate = "schema://database/{tableName}"
 *   override val name = "Database Table Schema"
 *   override val description = "Schema for any database table"
 *   override val mimeType = "application/json"
 *
 *   override suspend fun handler(
 *     request: ReadResourceRequest,
 *     variables: Map<String, String>,
 *   ): ReadResourceResult {
 *     val tableName = variables["tableName"]!!
 *     val schema = schemaService.getTableSchema(tableName)
 *     return ReadResourceResult(
 *       contents = listOf(TextResourceContents(schema.toJson(), request.uri))
 *     )
 *   }
 * }
 * ```
 *
 * ## URI Template Syntax
 *
 * Only RFC 6570 Level 1 syntax is supported: simple `{variableName}` expressions where each variable matches a single
 * URI path segment. Operator expressions (`{+var}`, `{#var}`, etc.) are treated as literals.
 *
 * Examples:
 * - `schema://database/{tableName}` - matches `schema://database/users`
 * - `docs://api/{version}/{endpoint}` - matches `docs://api/v2/users`
 * - `logs://service/{serviceName}/date/{date}` - matches `logs://service/auth/date/2025-01-01`
 *
 * @see McpResourceTemplateModule for registration and dependency injection
 * @see McpResource for static (non-parameterized) resources
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 */
@ExperimentalMiskApi
interface McpResourceTemplate {
  /**
   * RFC 6570 URI template for this resource.
   *
   * Uses Level 1 syntax with simple `{variableName}` expressions. Each variable matches a single URI path segment.
   */
  val uriTemplate: String

  /**
   * Human-readable name for this resource template.
   *
   * Exposed to clients and AI models to help them understand what resources this template serves.
   */
  val name: String

  /**
   * Human-readable description of this resource template's purpose.
   *
   * Should explain what family of resources this template provides access to and what the URI template variables
   * represent.
   */
  val description: String

  /**
   * MIME type indicating the format of the resource content.
   *
   * Defaults to "text/html" but should be overridden to match the actual content type being returned.
   */
  val mimeType: String
    get() = "text/html"

  /**
   * Handles incoming resource read requests matched by this template.
   *
   * @param request The incoming resource read request containing the resolved URI
   * @param variables Map of extracted URI template variable names to their matched values. For example, template
   *   `schema://database/{tableName}` matched against `schema://database/users` produces
   *   `mapOf("tableName" to "users")`.
   * @return The resource content with appropriate MIME type and encoding
   * @throws Exception if the resource access fails catastrophically
   */
  suspend fun handler(request: ReadResourceRequest, variables: Map<String, String>): ReadResourceResult
}
