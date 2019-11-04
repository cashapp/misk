package misk.web

import com.google.inject.Binder
import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import kotlin.reflect.KClass

class WebActionModule<A : WebAction> private constructor(
  val actionClass: KClass<A>,
  val url_path_prefix: String
) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry(actionClass, url_path_prefix))
    // Ensures that the action has an @Inject annotation and that its dependencies are satisfied
    binder().getProvider(actionClass.java)
  }

  override fun binder(): Binder = super.binder().skipSources(WebActionModule::class.java)

  companion object {
    inline fun <reified A : WebAction> create(): WebActionModule<A> = create(A::class)

    @JvmStatic
    fun <A : WebAction> create(actionClass: Class<A>): WebActionModule<A> {
      return create(actionClass.kotlin)
    }

    /**
     * Registers a web action.
     * @param actionClass: The web action to register.
     */
    fun <A : WebAction> create(actionClass: KClass<A>): WebActionModule<A> {
      return WebActionModule(actionClass, "/")
    }
    inline fun <reified A : WebAction> createWithPrefix(url_path_prefix: String): WebActionModule<A> =
        createWithPrefix(A::class, url_path_prefix)

    @JvmStatic
    fun <A : WebAction> createWithPrefix(
      actionClass: Class<A>,
      url_path_prefix: String
    ): WebActionModule<A> {
      return createWithPrefix(actionClass.kotlin, url_path_prefix)
    }

    /**
     * Registers a web action with a path prefix.
     * @param actionClass: The web action to register.
     * @param url_path_prefix: Defaults to "/". If not empty, must match pattern requirements:
     *   - must begin with "/"
     *   - any number of non-whitespace characters (including additional path segments or "/")
     *   - must terminate with a non-"/" because rest of path will start with "/"
     */
    fun <A : WebAction> createWithPrefix(
      actionClass: KClass<A>,
      url_path_prefix: String
    ): WebActionModule<A> {
      return WebActionModule(actionClass, url_path_prefix)
    }
  }
}
