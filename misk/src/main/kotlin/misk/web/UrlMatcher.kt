package misk.web

import jakarta.inject.Inject
import jakarta.inject.Singleton
import misk.web.actions.NotFoundAction
import misk.web.jetty.WebActionsServlet
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Provides a way to check if a URL has a bound action in the application. */
interface UrlMatcher {
  /**
   * Returns true if there is at least one bound action that can handle the given URL.
   *
   * @param url The URL to check (e.g., "http://example.com/api/users" or "https://example.com/api/users/123")
   * @return true if a bound action exists for this URL's path, false otherwise
   */
  fun hasBoundAction(url: String): Boolean
}

@Singleton
internal class RealUrlMatcher @Inject constructor(private val webActionsServlet: WebActionsServlet) : UrlMatcher {

  override fun hasBoundAction(url: String): Boolean {
    val httpUrl = url.toHttpUrl()

    return webActionsServlet.boundActions.any { boundAction ->
      val match = boundAction.matchByUrl(httpUrl)
      val actionClass = boundAction.action.functionAsJavaMethod?.declaringClass
      val isNotFoundAction = actionClass == NotFoundAction::class.java

      match != null && !isNotFoundAction
    }
  }
}
