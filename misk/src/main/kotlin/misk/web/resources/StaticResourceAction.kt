package misk.web.resources

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.resources.ResourceLoader
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.web.Get
import misk.web.HttpCall
import misk.web.Post
import misk.web.RequestContentType
import misk.web.Response
import misk.web.ResponseBody
import misk.web.ResponseContentType
import misk.web.actions.NotFoundAction
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import misk.web.toResponseBody
import okhttp3.Headers.Companion.headersOf
import okhttp3.HttpUrl
import okio.BufferedSink
import okio.BufferedSource
import wisp.logging.getLogger
import java.net.HttpURLConnection

/**
 * StaticResourceAction
 *
 * Sensitive resources with code file extensions like .class will not be returned by this action
 *   to prevent security vulnerabilities.
 *
 * This data class is used with Guice multibindings. Register instances by calling `multibind()`
 * in a `KAbstractModule`:
 * ```
 * multibind<StaticResourceEntry>()
 *   .toInstance(
 *     StaticResourceEntry(
 *       url_path_prefix = "/static/",
 *       resourcePath = "classpath:/web/static/"
 *     )
 *   )
 * install(WebActionModule.createWithPrefix<StaticResourceAction>(url_path_prefix = "/static/"))
 * ```
 */
@Singleton
class StaticResourceAction @Inject constructor(
  @JvmSuppressWildcards private val clientHttpCall: ActionScoped<HttpCall>,
  private val resourceLoader: ResourceLoader,
  private val resourceEntryFinder: ResourceEntryFinder
) : WebAction {
  @Get("/{path:.*}")
  @Post("/{path:.*}")
  @RequestContentType(MediaTypes.ALL)
  @ResponseContentType(MediaTypes.ALL)
  @Unauthenticated // TODO(adrw) https://github.com/square/misk/issues/429
  fun action(): Response<ResponseBody> {
    val httpCall = clientHttpCall.get()
    return getResponse(httpCall.url)
  }

  fun getResponse(url: HttpUrl): Response<ResponseBody> {
    val staticResourceEntry = resourceEntryFinder
      .staticResource(url) as StaticResourceEntry?
      ?: return NotFoundAction.response(url.encodedPath.drop(1))
    return MatchedResource(staticResourceEntry).getResponse(url)
  }

  private enum class MatchResult {
    NO_MATCH,
    RESOURCE,
    RESOURCE_DIRECTORY,
    SENSITIVE_RESOURCE,
  }

  private companion object {
    private val logger = getLogger<StaticResourceAction>()

    private val sensitiveResourceFileExtensions = setOf("class", "java", "kt", "proto")
  }

  private inner class MatchedResource(var matchedEntry: StaticResourceEntry) {
    fun getResponse(url: HttpUrl): Response<ResponseBody> {
      val urlPath = url.encodedPath
      return when (getMatchResult(urlPath)) {
        MatchResult.NO_MATCH -> when {
          !urlPath.endsWith("/") -> redirectResponse(normalizePathWithQuery(url))
          // actually return the resource, don't redirect. Path must stay the same since this will be handled by React router
          urlPath.endsWith("/") -> resourceResponse(
            normalizePath(matchedEntry.url_path_prefix),
          )

          else -> null
        }

        MatchResult.RESOURCE -> resourceResponse(urlPath)
        MatchResult.RESOURCE_DIRECTORY -> resourceResponse(normalizePathWithQuery(url))
        MatchResult.SENSITIVE_RESOURCE -> {
          logger.warn("Blocked access to sensitive resource: ${url.encodedPath}")
          NotFoundAction.response(url.encodedPath.drop(1))
        }
      } ?: NotFoundAction.response(url.encodedPath.drop(1))
    }

    /** Returns true if the mapped path exists on either the resource path or file system. */
    private fun getMatchResult(urlPath: String): MatchResult {
      val resourcePath = matchedEntry.resourcePath(urlPath)
      val maybeFileExtension = resourcePath.substringAfterLast('.', "")
      return when {
        // Prevent returning sensitive or code files, which could be a security risk
        maybeFileExtension in sensitiveResourceFileExtensions -> MatchResult.SENSITIVE_RESOURCE
        // Check if path is a directory before checking if it is a single resource
        resourceLoader.list(resourcePath).isNotEmpty() -> MatchResult.RESOURCE_DIRECTORY
        // If not a directory, check if resource
        resourceLoader.exists(resourcePath) -> MatchResult.RESOURCE
        else -> MatchResult.NO_MATCH
      }
    }

    /** Returns a source to the mapped path, or null if it doesn't exist. */
    fun open(urlPath: String): BufferedSource? {
      val resourcePath = matchedEntry.resourcePath(urlPath)
      return when {
        resourceLoader.exists(resourcePath) -> resourceLoader.open(resourcePath)!!
        else -> null
      }
    }

    private fun normalizePath(urlPath: String): String {
      return when {
        urlPath.endsWith("/") -> "${urlPath}index.html"
        !urlPath.endsWith("/") -> "$urlPath/"
        else -> urlPath
      }
    }

    private fun normalizePathWithQuery(url: HttpUrl): String = when {
      url.encodedQuery.isNullOrEmpty() -> normalizePath(url.encodedPath)
      else -> normalizePath(url.encodedPath) + "?" + url.encodedQuery
    }

    private fun resourceResponse(urlPath: String): Response<ResponseBody>? {
      return when (getMatchResult(urlPath)) {
        MatchResult.RESOURCE -> {
          val responseBody = object : ResponseBody {
            override fun writeTo(sink: BufferedSink) {
              open(urlPath)!!.use {
                sink.writeAll(it)
              }
            }
          }
          Response(
            body = responseBody,
            headers = headersOf(
              "Content-Type",
              MediaTypes.fromFileExtension(
                urlPath.substring(urlPath.lastIndexOf('.') + 1),
              ).toString(),
            ),
          )
        }

        else -> null
      }
    }

    private fun redirectResponse(urlPath: String): Response<ResponseBody> {
      return Response(
        body = "".toResponseBody(),
        statusCode = HttpURLConnection.HTTP_MOVED_TEMP,
        headers = headersOf("Location", urlPath),
      )
    }
  }
}
