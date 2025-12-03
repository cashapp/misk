package misk.mcp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import kotlin.annotation.AnnotationRetention.RUNTIME

/**
 * Annotation used to provide human-readable descriptions for data class parameters in MCP (Model Context Protocol) tools.
 *
 * When applied to a parameter in a data class that is used as input for an MCP tool, this annotation
 * adds a `description` field to the generated JSON schema. This description helps users understand
 * the purpose and expected format of each parameter when interacting with the tool.
 *
 * The annotation is processed during JSON schema generation and the description text is included
 * in the resulting schema to provide better documentation for API consumers.
 *
 * ## Usage
 *
 * Apply this annotation to data class parameters that will be used as MCP tool inputs:
 *
 * ```kotlin
 * data class CreateUserRequest(
 *   @Description("The unique username for the new user account")
 *   val username: String,
 *
 *   @Description("The user's email address for notifications and login")
 *   val email: String,
 *
 *   @Description("Optional display name for the user profile")
 *   val displayName: String? = null,
 *
 *   @Description("User's age in years (must be 18 or older)")
 *   val age: Int
 * )
 * ```
 *
 * ## Generated JSON Schema
 *
 * The above example would generate a JSON schema similar to:
 *
 * ```json
 * {
 *   "type": "object",
 *   "properties": {
 *     "username": {
 *       "type": "string",
 *       "description": "The unique username for the new user account"
 *     },
 *     "email": {
 *       "type": "string",
 *       "description": "The user's email address for notifications and login"
 *     },
 *     "displayName": {
 *       "type": "string",
 *       "description": "Optional display name for the user profile"
 *     },
 *     "age": {
 *       "type": "integer",
 *       "description": "User's age in years (must be 18 or older)"
 *     }
 *   },
 *   "required": ["username", "email", "age"]
 * }
 * ```
 *
 * ## Best Practices
 *
 * - **Be descriptive but concise**: Provide enough information to understand the parameter's purpose
 *   without being overly verbose
 * - **Include format expectations**: Mention expected formats, ranges, or constraints when relevant
 * - **Explain business context**: Help users understand not just what the parameter is, but why it's needed
 * - **Use consistent terminology**: Maintain consistent language across related parameters and tools
 * - **Consider the audience**: Write descriptions that are appropriate for the intended users of the tool
 *
 * ## Supported Types
 *
 * This annotation works with all parameter types supported by the JSON schema generation:
 * - Primitive types (String, Int, Long, Float, Double, Boolean)
 * - Nullable types (String?, Int?, etc.)
 * - Collections (List, Set, Map)
 * - Nested data classes
 * - Optional parameters with default values
 *
 * @param value The human-readable description text that will be included in the generated JSON schema.
 *              Should be a clear, concise explanation of the parameter's purpose and any relevant constraints.
 *
 * @see misk.mcp.internal.generateJsonSchema
 * @since 1.0.0
 */
@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(RUNTIME)
@SerialInfo
annotation class Description(val value: String)
