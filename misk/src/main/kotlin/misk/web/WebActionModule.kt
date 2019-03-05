package misk.web

import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import kotlin.reflect.KClass

class WebActionModule<A : WebAction> private constructor(
  val actionClass: KClass<A>,
  val url_path_prefix: String
) : KAbstractModule() {
  override fun configure() {
    bind(actionClass.java)
    multibind<WebActionEntry>().toInstance(WebActionEntry(actionClass, url_path_prefix))
  }

  companion object {
    /**
     * Registers a web action.
     * @param actionClass: The web action to register.
     */
    inline fun <reified A : WebAction> forAction(): WebActionModule<A> = forAction(A::class)

    /**
     * Registers a web action.
     * @param actionClass: The web action to register.
     */
    @JvmStatic
    fun <A : WebAction> forAction(actionClass: Class<A>): WebActionModule<A> {
      return forAction(actionClass.kotlin)
    }

    /**
     * Registers a web action.
     */
    fun <A : WebAction> forAction(actionClass: KClass<A>): WebActionModule<A> {
      return WebActionModule(actionClass, "/")
    }

    /**
     * Registers a web action with a path prefix.
     * @param url_path_prefix: defaults to "/". If not empty, must match pattern requirements:
     *   - must begin with "/"
     *   - any number of non-whitespace characters (including additional path segments or "/")
     *   - must terminate with a non-"/" because rest of path will start with "/"
     */
    inline fun <reified A : WebAction> forPrefixedAction(url_path_prefix: String): WebActionModule<A> =
        forPrefixedAction(A::class, url_path_prefix)

    /**
     * Registers a web action with a path prefix.
     * @param actionClass: The web action to register.
     * @param url_path_prefix: Defaults to "/". If not empty, must match pattern requirements:
     *   - must begin with "/"
     *   - any number of non-whitespace characters (including additional path segments or "/")
     *   - must terminate with a non-"/" because rest of path will start with "/"
     */
    @JvmStatic
    fun <A : WebAction> forPrefixedAction(
      actionClass: Class<A>,
      url_path_prefix: String
    ): WebActionModule<A> {
      return forPrefixedAction(actionClass.kotlin, url_path_prefix)
    }

    /**
     * Registers a web action with a path prefix.
     * @param actionClass: The web action to register.
     * @param url_path_prefix: Defaults to "/". If not empty, must match pattern requirements:
     *   - must begin with "/"
     *   - any number of non-whitespace characters (including additional path segments or "/")
     *   - must terminate with a non-"/" because rest of path will start with "/"
     */
    fun <A : WebAction> forPrefixedAction(
      actionClass: KClass<A>,
      url_path_prefix: String
    ): WebActionModule<A> {
      return WebActionModule(actionClass, url_path_prefix)
    }
  }
}
