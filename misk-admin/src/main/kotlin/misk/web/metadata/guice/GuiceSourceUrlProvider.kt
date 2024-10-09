package misk.web.metadata.guice

/**
 * Provides URLs for navigating to source code in Guice tab in Misk's admin console.
 */
interface GuiceSourceUrlProvider {
  /**
   * Given a source string, return a URL that can be used to navigate to the source.
   * Return null if no URL can be determined.
   */
  fun urlForSource(source: String): String?
}
