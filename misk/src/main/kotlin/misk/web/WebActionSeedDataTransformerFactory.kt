package misk.web

import misk.Action
import misk.scope.SeedDataTransformer

/**
 * Creates a [SeedDataTransformer] for a specific web [Action].
 *
 * This interface is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 *
 * ```
 * multibind<WebActionSeedDataTransformerFactory>().toInstance(WebActionSeedDataTransformerFactory(...))
 * ```
 */
interface WebActionSeedDataTransformerFactory {
  /** Returns null to not transform the map on [action]. */
  fun create(
    pathPattern: PathPattern,
    action: Action,
  ): SeedDataTransformer?
}
