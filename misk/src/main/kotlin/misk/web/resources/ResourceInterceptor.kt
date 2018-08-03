package misk.web.resources

import misk.Action
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ResourceInterceptor
 *
 * Returns requested web resources from Jar locations
 */

@Singleton
class ResourceInterceptor(
  private val mappings: List<Entry> = listOf()
) : NetworkInterceptor {
  override fun intercept(chain: NetworkChain): Response<*> {
//    val matchedMapping = ResourceInterceptorCommon.findMappingFromUrl(mappings, chain.request.url) as Entry? ?: return chain.proceed(chain.request)
    // TODO(adrw) finish building out Jar resource forwarding
    return chain.proceed(chain.request)
  }

  /**
   * Maps URLs requested against this server to Jar paths where static resources live
   *
   * url_path_prefix: `/_admin/`
   * jar_path: `/web/admin/`
   *
   * An incoming request then for `/_admin/config.js` would route to `/web/admin/config.js`
   *
   *
   *
   * This data class is used with Guice multibindings. Register instances by calling `multibind()`
   * in a `KAbstractModule`:
   *
   * ```
   * multibind<ResourceInterceptor.Entry>().toInstance(ResourceInterceptor.Entry(...))
   * ```
   */
  data class Entry(
    override val url_path_prefix: String,
    val jar_path: String
  ) : ResourceInterceptorCommon.Entry {
    init {
      require(url_path_prefix.startsWith("/") &&
          url_path_prefix.startsWith("/") &&
          jar_path.startsWith("/") &&
          jar_path.endsWith("/")
      )
    }
  }

  class Factory @Inject internal constructor() : NetworkInterceptor.Factory {
    @Inject private lateinit var mappings: List<Entry>
    override fun create(action: Action): NetworkInterceptor? {
      return ResourceInterceptor(mappings)
    }
  }
}