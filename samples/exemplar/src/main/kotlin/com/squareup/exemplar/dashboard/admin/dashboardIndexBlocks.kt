package com.squareup.exemplar.dashboard.admin

import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.h3
import kotlinx.html.li
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe
import misk.MiskCaller
import misk.web.dashboard.AdminDashboard
import misk.web.dashboard.DashboardTab
import misk.web.v2.DashboardIndexAccessBlock
import misk.web.v2.DashboardIndexBlock
import wisp.deployment.Deployment

val dashboardIndexAccessBlock =
  DashboardIndexAccessBlock<AdminDashboard> {
    appName: String,
    deployment: Deployment,
    caller: MiskCaller?,
    authenticatedTabs: List<DashboardTab>,
    dashboardTabs: List<DashboardTab> ->
    val dashboardTabCapabilities = dashboardTabs.flatMap { it.capabilities }.toSet()

    val h3TextColor =
      when {
        authenticatedTabs.isEmpty() -> "text-red-800"
        else -> "text-blue-800"
      }
    val hoverTextColor =
      when {
        authenticatedTabs.isEmpty() -> "hover:text-red-500"
        else -> "hover:text-blue-500"
      }
    val textColor =
      when {
        authenticatedTabs.isEmpty() -> "text-red-700"
        else -> "text-blue-700"
      }
    val backgroundColor =
      when {
        authenticatedTabs.isEmpty() -> "bg-red-50"
        else -> "bg-blue-50"
      }
    val iconSvg =
      when {
        authenticatedTabs.isEmpty() -> {
          """
          <svg class="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clip-rule="evenodd" />
          </svg>
          """
            .trimIndent()
        }

        else -> {
          """
          <svg class="h-5 w-5 text-blue-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
          </svg>
          """
            .trimIndent()
        }
      }

    div("rounded-md $backgroundColor p-4") {
      div("flex") {
        div("flex-shrink-0") { unsafe { raw(iconSvg) } }
        div("ml-3 flex-1 md:flex md:justify-between") {
          div {
            h3("text-sm font-medium $h3TextColor") { +"""Authenticated Access""" }
            div("mt-2 text-sm $textColor") {
              ul("list-disc space-y-1 pl-5") {
                role = "list"
                li {
                  +"""You have access to ${authenticatedTabs.size} / ${dashboardTabs.size} tabs with your capabilities ${caller?.capabilities}."""
                }
                li {
                  +"""Missing access to some dashboard tabs? Ensure you have one of the required capabilities $dashboardTabCapabilities in Access Registry."""
                }
              }
            }
          }

          p("mt-3 text-sm md:ml-6 md:mt-0 align-middle float-right") {
            a(classes = "whitespace-nowrap font-medium $textColor $hoverTextColor") {
              href = "#registry/${caller?.principal}"
              +"""Access Registry"""
              span {
                attributes["aria-hidden"] = "true"
                +""" →"""
              }
            }
          }
        }
      }
    }
  }

val dashboardIndexBlock1 = DashboardIndexBlock<AdminDashboard> { p { +"""Content 1""" } }
val dashboardIndexBlock2 = DashboardIndexBlock<AdminDashboard> { p { +"""Content 2""" } }
