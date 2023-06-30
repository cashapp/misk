package misk.web.v2

import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.span
import misk.MiskCaller
import misk.scope.ActionScoped
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * Builds dashboard UI for index homepage.
 */
@Singleton
class DashboardIndexAction @Inject constructor(
  private val callerProvider: ActionScoped<MiskCaller?>,
  private val dashboardPageLayout: DashboardPageLayout
) : WebAction {
  @Get("/")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String = dashboardPageLayout
    .newBuilder()
    .title { appName, dashboardHomeUrl, _ -> "Home | $appName ${dashboardHomeUrl?.dashboardAnnotationKClass?.titlecase() ?: ""}" }
    .build { appName, dashboardHomeUrl, _ ->
      div("center container p-8") {
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
