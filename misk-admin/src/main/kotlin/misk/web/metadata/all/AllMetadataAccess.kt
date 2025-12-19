package misk.web.metadata.all

/**
 * Bind to set access for the exposed service metadata.
 *
 * ```kotlin
 * // Give internal scraping services access to all metadata for the service
 * multibind<AccessAnnotationEntry>().toInstance(
 *   AccessAnnotationEntry<AllMetadataAccess>(
 *   services = listOf("internal_security_scraper_service"))
 * )
 * ```
 */
@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION) annotation class AllMetadataAccess
