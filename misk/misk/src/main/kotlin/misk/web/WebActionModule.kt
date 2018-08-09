package misk.web

import misk.inject.KAbstractModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import kotlin.reflect.KClass

@Suppress("DEPRECATION")
class WebActionModule<A : WebAction> private constructor(
  val webActionClass: KClass<A>
) : KAbstractModule() {
  override fun configure() {
    multibind<WebActionEntry>().toInstance(WebActionEntry(webActionClass))
  }

  companion object {
    @Deprecated("use multibind<WebActionEntry>().toInstance(WebActionEntry(...))")
    inline fun <reified A : WebAction> create(): WebActionModule<A> = create(A::class)

    @Deprecated("use multibind<WebActionEntry>().toInstance(WebActionEntry(...))")
    @JvmStatic
    fun <A : WebAction> create(webActionClass: Class<A>): WebActionModule<A> {
      return create(webActionClass.kotlin)
    }

    @Deprecated("use multibind<WebActionEntry>().toInstance(WebActionEntry(...))")
    fun <A : WebAction> create(webActionClass: KClass<A>): WebActionModule<A> {
      return WebActionModule(webActionClass)
    }
  }
}
