package misk.web.v2

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.TagConsumer
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.span
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.security.authz.Unauthenticated
import misk.tailwind.components.AlertError
import misk.tailwind.components.AlertInfo
import misk.web.Get
import misk.web.PathParam
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.DashboardTab
import misk.web.dashboard.ValidWebEntry.Companion.slugify
import misk.web.mediatype.MediaTypes
import wisp.deployment.Deployment
import kotlin.reflect.KClass

/**
 * Builds dashboard UI for index homepage.
 */
@Singleton
class DashboardIndexAction @Inject constructor(
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val dashboardPageLayout: DashboardPageLayout,
  private val allTabs: List<DashboardTab>,
  private val allDashboardIndexAccessBlocks: List<DashboardIndexAccessBlock>,
  private val allDashboardIndexBlocks: List<DashboardIndexBlock>,
  private val deployment: Deployment,
) : WebAction {
  @Get("/{rest:.*}")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @Unauthenticated
  fun get(@PathParam rest: String?): String = dashboardPageLayout
    .newBuilder()
    .title { appName, dashboardHomeUrl, _ -> "Home | $appName ${dashboardHomeUrl?.dashboardAnnotationKClass?.titlecase() ?: ""}" }
    .build { appName, dashboardHomeUrl, _ ->
      div("center container p-8") {
        if (rest?.isNotBlank() == true) {
          // Show 404 message if the tab is not found.
          AlertError("""Dashboard tab not found for: ${rest.removeSuffix("/")}""")
          AlertInfo("Check your DashboardModule installation to ensure that the slug, urlPathPrefix, and iframePath matches your frontend location.")
        }

        // Welcome
        h1("text-2xl") {
          +"""Welcome, """
          span("font-bold font-mono") { +"""${callerProvider.get()?.user}""" }
          +"""!"""
        }
        h2("text-xl py-2") {
          +"""This is the ${dashboardHomeUrl?.dashboardAnnotationKClass?.titlecase()} for """
          span("font-bold font-mono") { +appName }
          +"""."""
        }

        // Access notice block.
        val dashboardTabs =
          allTabs.filter { it.dashboard_slug == dashboardHomeUrl?.dashboard_slug }
        val authenticatedTabs = dashboardTabs.filter {
          callerProvider.get()?.hasCapability(it.capabilities) ?: false
        }

        allDashboardIndexAccessBlocks.firstOrNull { slugify(it.annotation) == dashboardHomeUrl?.dashboard_slug }?.block?.let {
          div("pt-5") {
            it(appName, deployment, callerProvider.get(), authenticatedTabs, dashboardTabs)
          }
        }

        if (authenticatedTabs.isNotEmpty()) {
          // Only shown if authenticated for at least 1 tab to limit potential for data leak since index is unauthenticated.
          // Other content for the dashboard homepage.
          allDashboardIndexBlocks.filter { slugify(it.annotation) == dashboardHomeUrl?.dashboard_slug }.forEach {
            div("pt-5") {
              it.block(this@build)
            }
          }
        }
      }
    }

  companion object {
    fun KClass<out Annotation>.titlecase(): String {
      val title = StringBuilder()
      val name = this.simpleName ?: ""
      name.forEachIndexed { index, c ->
        if (index < name.lastIndex && name[index + 1].isUpperCase()) {
          title.append("$c ")
        } else {
          title.append(c)
        }
      }
      return title.toString()
    }
  }
}


/**
 * Bind to set custom access notice for the dashboard home page.
 *
 * ```kotlin
 * multibind<DashboardIndexAccessBlock>().toInstance(
 *   DashboardIndexAccessBlock<AdminDashboard>() {
 *     p { +"""You have access to ${authenticatedTabs.size} / ${dashboardTabs.size} tabs.""" }
 *     p { +"""Add the necessary permissions to your user in the company registry.""" }
 *   }
 * )
 * ```
 */
data class DashboardIndexAccessBlock @JvmOverloads constructor(
  val annotation: KClass<out Annotation>,
  val block: TagConsumer<*>.(appName: String, deployment: Deployment, caller: MiskCaller?, authenticatedTabs: List<DashboardTab>, dashboardTabs: List<DashboardTab>) -> Unit
)

inline fun <reified T : Annotation> DashboardIndexAccessBlock(
  noinline block: TagConsumer<*>.(appName: String, deployment: Deployment, caller: MiskCaller?, authenticatedTabs: List<DashboardTab>, dashboardTabs: List<DashboardTab>) -> Unit
): DashboardIndexAccessBlock = DashboardIndexAccessBlock(T::class, block)

/**
 * Bind to set custom content for the dashboard home page.
 *
 * ```kotlin
 * multibind<DashboardIndexBlock>().toInstance(
 *   DashboardIndexBlock(
 *     p { +"""This content will show up on the dashboard homepage.""" }
 *   )
 * )
 * ```
 */
data class DashboardIndexBlock @JvmOverloads constructor(
  val annotation: KClass<out Annotation>,
  val block: TagConsumer<*>.() -> Unit
)

inline fun <reified T : Annotation> DashboardIndexBlock(
  noinline block: TagConsumer<*>.() -> Unit
): DashboardIndexBlock = DashboardIndexBlock(T::class, block)
