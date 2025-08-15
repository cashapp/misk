package misk.mcp.internal

import jakarta.inject.Qualifier

/**
 * Qualifier for the [Json] instance used in the MCP module.
 * This is used to differentiate the MCP-specific JSON configuration from other JSON configurations
 */
@Qualifier
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
internal annotation class MiskMcp
