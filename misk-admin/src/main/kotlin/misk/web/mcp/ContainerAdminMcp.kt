package misk.web.mcp

import jakarta.inject.Qualifier

/** Qualifier annotation for the Container Admin MCP server group. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
annotation class ContainerAdminMcp
