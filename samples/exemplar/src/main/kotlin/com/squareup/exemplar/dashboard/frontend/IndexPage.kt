package com.squareup.exemplar.dashboard.frontend

import com.squareup.exemplar.dashboard.admin.AlphaIndexAction
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.role
import kotlinx.html.ul
import misk.config.AppName
import misk.hotwire.buildHtml
import misk.turbo.turbo_frame
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.dashboard.HtmlLayout
import misk.web.mediatype.MediaTypes
import wisp.deployment.Deployment

/** Example page from Tailwind UI https://tailwindui.com/components/ecommerce/page-examples/storefront-pages */
@Singleton
class IndexPage @Inject constructor(@AppName private val appName: String, private val deployment: Deployment) :
  WebAction {
  @Get("/")
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String {
    return buildHtml {
      HtmlLayout(appRoot = "/app", title = "$appName frontend", playCdn = deployment.isLocalDevelopment) {
        turbo_frame(id = "tab") {
          div("p-5") {
            h1("py-5") { +"""UI Examples""" }
            nav("flex flex-1 flex-col") {
              attributes["aria-label"] = "Sidebar"
              ul("-mx-2 space-y-1") {
                role = "list"
                li {
                  //                +"""<!-- Current: "bg-gray-50 text-indigo-600", Default: "text-gray-700
                  // hover:text-indigo-600 hover:bg-gray-50" -->"""
                  a(
                    classes =
                      "text-gray-700 hover:text-indigo-600 hover:bg-gray-50 group flex gap-x-3 rounded-md p-2 pl-3 text-sm leading-6 font-semibold"
                  ) {
                    href = SimplePage.PATH
                    +"""Simple"""
                  }
                }
                li {
                  a(
                    classes =
                      "text-gray-700 hover:text-indigo-600 hover:bg-gray-50 group flex gap-x-3 rounded-md p-2 pl-3 text-sm leading-6 font-semibold"
                  ) {
                    href = EcommerceLandingPage.PATH
                    +"""Ecommerce Landing Page"""
                  }
                }
                li {
                  a(
                    classes =
                      "text-gray-700 hover:text-indigo-600 hover:bg-gray-50 group flex gap-x-3 rounded-md p-2 pl-3 text-sm leading-6 font-semibold"
                  ) {
                    href = GraphD3JsPage.PATH
                    +"""Graph with D3.js"""
                  }
                }
                li {
                  a(
                    classes =
                      "text-gray-700 hover:text-indigo-600 hover:bg-gray-50 group flex gap-x-3 rounded-md p-2 pl-3 text-sm leading-6 font-semibold"
                  ) {
                    href = "/support/"
                    +"""Support Custom Dashboard"""
                  }
                }
                li {
                  a(
                    classes =
                      "text-gray-700 hover:text-indigo-600 hover:bg-gray-50 group flex gap-x-3 rounded-md p-2 pl-3 text-sm leading-6 font-semibold"
                  ) {
                    href = AlphaIndexAction.PATH
                    +"""Custom Admin Dashboard Tab"""
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
