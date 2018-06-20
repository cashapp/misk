package misk.web.actions

import misk.logging.getLogger
import misk.web.NetworkChain
import misk.web.NetworkInterceptor
import misk.web.Response
import okhttp3.HttpUrl
import java.io.File.separator

class UpstreamResourceInterceptor(
  private val mappings: MutableList<out Mapping>
) : NetworkInterceptor {
  internal val logger = getLogger<UpstreamResourceInterceptor>()

  @Suppress("UNUSED_PARAMETER")
  override fun intercept(chain: NetworkChain): Response<*> {
    // TODO: call the configured upstream (ie. webpack) server
    // if it 404s, call chain.proceed()
    // otherwise return the upstream's response

// request has path, find mapping that satisfies
//    if mapping found, make http request to target server
//    for that request, do fancy stuff to create new url
//    ie. drop everything below localprefix path, append to new
//    Then. make okhttp request with that path, same headers, same method, body
//    execute request, take response, route response back

//  if no mapping found, call chain.proceed()


//    methods misk request -> okhttp requst / misk response -> okhjttp response will be shared
//    put them in misk request/response. misk request.toOkHttp3 and okhttp3 response.toMiskResponse


//    bodies aren't the same. build sink from source by writing source to the sink
//    ignore web socket completely

// how to make adding mapping easy with new modules
//    Injection will do wiring
//    but... is rule going to be that when new service is built that redirection automatically happens on set paths

//    Extra challenge: deploying in production, bundle JS in JAR. In dev, JS served by webpack dev server
//    new service (ie. url shorteneer) consumes misk package as binary.


//    will have to inject/create okhttp3 client





//    val incomingScheme = HttpUrl.parse(chain.request.url.toString())!!.scheme()
//    val incomingHost = HttpUrl.parse(chain.request.url.toString())!!.host()
//    val incomingPort = HttpUrl.parse(chain.request.url.toString())!!.port()
    val incomingPathSegments = HttpUrl.parse(chain.request.url.toString())!!.pathSegments()

    for (mapping in this.mappings) {
//      val a = incomingPathSegments.joinToString("/").contains(mapping.localPathPrefix)
//      val c = incomingPathSegments.joinToString("/")
//      val d = mapping.localPathPrefix

      val localPathSegments = mapping.localPathPrefix.drop(1).dropLast(1).split('/')

      var match = true
      for ((i, localPathSegment) in localPathSegments.withIndex()) {
        if (i > incomingPathSegments.size-1) break
        if (incomingPathSegments[i] != localPathSegment) match = false
      }

      logger.info { match.toString()  }

      if (match) {
        val upstreamScheme = HttpUrl.parse(mapping.upstreamBaseUrl.toString())!!.scheme()
        val upstreamHost = HttpUrl.parse(mapping.upstreamBaseUrl.toString())!!.host()
        val upstreamPort = HttpUrl.parse(mapping.upstreamBaseUrl.toString())!!.port()
        val upstreamPathSegments = HttpUrl.parse(mapping.upstreamBaseUrl.toString())!!.pathSegments()

        val newUrl = HttpUrl.Builder()
            .scheme(upstreamScheme)
            .host(upstreamHost)
            .port(upstreamPort)
            .addPathSegments(upstreamPathSegments.joinToString("/"))

        val newUrl2 = newUrl
        logger.info { newUrl2 }
      }

      val a = match
      logger.info { a }
    }

    return chain.proceed(chain.request)
  }

  /**
   * Imagine that we had the following Mapping:
   *
   * localPathPrefix: `/_admin/`
   * upstreamBaseUrl: `http://localhost:3000/`
   *
   * An incoming request for `/_admin/config.js` would route to `http://localhost:3000/config.js`.
   */
  data class Mapping(
    val localPathPrefix: String,
    val upstreamBaseUrl: HttpUrl
  ) {
    init {
      require(localPathPrefix.endsWith("/") &&
          localPathPrefix.startsWith("/") &&
          upstreamBaseUrl.encodedPath().endsWith("/"))
    }
  }
}