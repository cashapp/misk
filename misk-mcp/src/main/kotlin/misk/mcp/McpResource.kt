package misk.mcp

import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import misk.annotation.ExperimentalMiskApi

/**
 * Abstraction for a resource in the Model Context Protocol (MCP) specification.
 *
 * This interface represents a resource that can be exposed through an MCP server,
 * allowing AI models and other clients to discover and access structured data or
 * content. Resources are one of the core primitives in MCP, enabling models to
 * retrieve information, documentation, schemas, or any other contextual data
 * that can inform their responses.
 *
 * The MCP specification defines resources as addressable pieces of data that can
 * be retrieved by clients using a URI-based addressing scheme. Each resource must
 * have a unique URI within its server context and provide metadata describing
 * its content type and purpose.
 *
 * ## Implementation Requirements
 *
 * Implementations must:
 * - Provide a unique [uri] within the server context
 * - Include a human-readable [name] for the resource
 * - Include a [description] explaining the resource's content and purpose
 * - Optionally specify a [mimeType] (defaults to "text/html")
 * - Implement the [handler] function to return the resource content
 *
 * ## Registration
 *
 * Resources should be registered with an MCP server using [McpResourceModule]:
 *
 * ```kotlin
 * // In your Guice module configuration
 * install(McpResourceModule.create<MyCustomResource>("myServerName"))
 * ```
 *
 * ## Example Implementation
 *
 * ```kotlin
 * @Singleton
 * class DatabaseSchemaResource @Inject constructor(
 *   private val schemaService: SchemaService
 * ) : McpResource {
 *   override val uri = "schema://database/users"
 *   override val name = "User Database Schema"
 *   override val description = "Complete schema definition for the users database table"
 *   override val mimeType = "application/json"
 *
 *   override suspend fun handler(request: ReadResourceRequest): ReadResourceResult {
 *     val schema = schemaService.getUserTableSchema()
 *     val schemaJson = Json.encodeToString(schema)
 *
 *     return ReadResourceResult(
 *       contents = listOf(
 *         ResourceContent(
 *           uri = uri,
 *           mimeType = mimeType,
 *           text = schemaJson
 *         )
 *       )
 *     )
 *   }
 * }
 * ```
 *
 * ## Common Resource Types
 *
 * Resources can represent various types of content:
 * - **Documentation**: API docs, user guides, technical specifications
 * - **Schemas**: Database schemas, API schemas, configuration schemas
 * - **Configuration**: Application settings, environment configurations
 * - **Data**: Static datasets, reference data, lookup tables
 * - **Templates**: Code templates, document templates, prompt templates
 *
 * ## URI Conventions
 *
 * Resource URIs should follow consistent patterns:
 * - `schema://database/table_name` - Database schemas
 * - `docs://api/endpoint_name` - API documentation
 * - `config://service/setting_name` - Configuration values
 * - `data://reference/dataset_name` - Reference datasets
 *
 * @see <a href="https://modelcontextprotocol.io/specification/2025-06-18">MCP Specification</a>
 * @see McpResourceModule for registration and dependency injection
 */
@ExperimentalMiskApi
interface McpResource {
  /**
   * The unique URI identifier for this resource within the MCP server context.
   *
   * Must be unique among all resources registered with the same server instance.
   * Should follow URI conventions and use descriptive schemes and paths that
   * clearly indicate the resource's purpose and location.
   */
  val uri: String

  /**
   * Human-readable name for this resource.
   *
   * This name is exposed to clients and AI models to help them understand
   * what this resource contains. Should be descriptive and concise, clearly
   * indicating the resource's content or purpose.
   */
  val name: String

  /**
   * Human-readable description of this resource's content and purpose.
   *
   * This description is exposed to clients and AI models to help them understand
   * when and how to use this resource. Should explain what information the
   * resource provides and in what context it would be useful.
   */
  val description: String

  /**
   * MIME type indicating the format of the resource content.
   *
   * Defaults to "text/html" but should be overridden to match the actual
   * content type being returned. Common types include:
   * - "application/json" for JSON data
   * - "text/plain" for plain text
   * - "text/markdown" for Markdown content
   * - "application/xml" for XML data
   */
  val mimeType: String get() = "text/html"

  /**
   * Handles incoming resource read requests.
   *
   * This suspend function processes the resource request, retrieves or generates
   * the appropriate content, and returns structured results. The function should
   * handle errors gracefully and return appropriate error responses when the
   * resource cannot be accessed or generated.
   *
   * @param request The incoming resource read request containing URI and metadata
   * @return The resource content with appropriate MIME type and encoding
   * @throws Exception if the resource access fails catastrophically
   */
  suspend fun handler(request: ReadResourceRequest): ReadResourceResult
}
