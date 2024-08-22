package misk.tailwind.pages

import kotlinx.html.ButtonType
import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.h1
import kotlinx.html.img
import kotlinx.html.li
import kotlinx.html.main
import kotlinx.html.nav
import kotlinx.html.role
import kotlinx.html.span
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import misk.tailwind.Link
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon
import wisp.deployment.Deployment

data class MenuSection(
  val title: String,
  val links: List<Link>
)

// TODO fix scrolling of non-iframe tabs scrolling the sidebar color
fun TagConsumer<*>.Navbar(
  appName: String,
  deployment: Deployment,
  homeHref: String,
  menuSections: List<MenuSection> = listOf(),
  content: TagConsumer<*>.() -> Unit = {}
) {
  div("bg-gray-900") {
    attributes["data-controller"] = "toggle"

    // +"""<!-- Off-canvas menu for mobile, show/hide based on off-canvas menu state. -->"""
    div("hidden relative z-50 xl:hidden") {
      role = "dialog"
      attributes["aria-modal"] = "true"
      attributes["data-toggle-target"] = "toggleable"
      attributes["data-css-class"] = "hidden"
//      +"""<!--
//      Off-canvas menu backdrop, show/hide based on off-canvas menu state.
//
//      Entering: "transition-opacity ease-linear duration-300"
//        From: "opacity-0"
//        To: "opacity-100"
//      Leaving: "transition-opacity ease-linear duration-300"
//        From: "opacity-100"
//        To: "opacity-0"
//    -->"""
      div("fixed inset-0 bg-gray-900/80") {
      }
      div("fixed inset-0 flex") {
//        +"""<!--
//        Off-canvas menu, show/hide based on off-canvas menu state.
//
//        Entering: "transition ease-in-out duration-300 transform"
//          From: "-translate-x-full"
//          To: "translate-x-0"
//        Leaving: "transition ease-in-out duration-300 transform"
//          From: "translate-x-0"
//          To: "-translate-x-full"
//      -->"""
        div("relative mr-16 flex w-full max-w-xs flex-1") {
//          +"""<!--
//          Close button, show/hide based on off-canvas menu state.
//
//          Entering: "ease-in-out duration-300"
//            From: "opacity-0"
//            To: "opacity-100"
//          Leaving: "ease-in-out duration-300"
//            From: "opacity-100"
//            To: "opacity-0"
//        -->"""
          // +"""<!-- Sidebar component, swap this element with another sidebar if you like -->"""
          div("hidden flex grow flex-col gap-y-5 overflow-y-auto bg-gray-900 px-6 ring-1 ring-white/10") {
            attributes["data-toggle-target"] = "toggleable"
            attributes["data-css-class"] = "hidden"

            div("flex h-16 shrink-0 items-center") {
              if (menuSections.isNotEmpty()) {
                button(classes = "-m-2.5 p-2.5 text-white xl:hidden") {
                  type = ButtonType.button
                  attributes["data-action"] = "toggle#toggle"
                  span("sr-only") { +"""Close sidebar""" }
                  // TODO add to Heroicons
                  unsafe {
                    raw(
                      """
                  <svg class="h-6 w-6 text-white" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                  </svg>
                  """.trimIndent()
                    )
                  }
                }
              }
            }
//            div("flex h-16 shrink-0 items-center") {
//              unsafe {
//                raw(
//                  """
//                    <svg class="h-6 w-6 text-white" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
//                      <path fill-rule="evenodd" d="M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10zm0 5.25a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75a.75.75 0 01-.75-.75z" clip-rule="evenodd" />
//                    </svg>
//                    """.trimIndent()
//                )
//              }
//            }
            NavMenu(menuSections)
          }
        }
      }
    }
    // +"""<!-- Static sidebar for desktop -->"""
    div("bg-gray-900 hidden xl:fixed xl:inset-y-0 xl:z-50 xl:flex xl:w-72 xl:flex-col") {
      // +"""<!-- Sidebar component, swap this element with another sidebar if you like -->"""
      div("flex grow flex-col gap-y-5 overflow-y-auto bg-black/10 px-6 ring-1 ring-white/5") {
        div("flex h-16 shrink-0 items-center") {
          a {
            href = homeHref

            img(classes = "h-8 w-auto") {
              src = "/static/favicon.ico"
              alt = "Dashboard Logo"
            }
          }
        }
        NavMenu(menuSections)
      }
    }
    div("xl:pl-72") {
      // +"""<!-- Sticky search header -->"""
      div("fixed w-full z-40 flex h-16 shrink-0 items-center gap-x-6 border-b-4 ${deployment.asBorderColor()} bg-gray-900 px-6 shadow-sm sm:px-6 lg:px-6") {
        if (menuSections.isNotEmpty()) {
          button(classes = "-m-2.5 p-2.5 text-white xl:hidden") {
            attributes["data-action"] = "toggle#toggle"
            type = ButtonType.button
            span("sr-only") { +"""Open sidebar""" }
            // TODO add to Heroicons
            unsafe {
              raw(
                """
                <svg class="h-6 w-6" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fill-rule="evenodd" d="M2 4.75A.75.75 0 012.75 4h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 4.75zM2 10a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75A.75.75 0 012 10zm0 5.25a.75.75 0 01.75-.75h14.5a.75.75 0 010 1.5H2.75a.75.75 0 01-.75-.75z" clip-rule="evenodd" />
                </svg>
                """.trimIndent()
              )
            }
          }
        }
        div("flex flex-1 gap-x-4 self-stretch lg:gap-x-6 py-4") {
          a(homeHref) {
            h1("text-white text-lg") { +appName.uppercase() }
          }
          h1("${deployment.asTextColor()} text-lg") {
            +deployment.mapToEnvironmentName().uppercase()
          }
        }
      }
      main("h-full bg-white") {
        div("pt-16") {
          content()
        }
      }
    }
  }
}

/** Classes must be complete or they are not included in the production CSS. */
private fun Deployment.asTextColor() = when {
  isProduction -> "text-red-500"
  isStaging -> "text-green-500"
  isTest -> "text-white"
  else -> "text-blue-500"
}

/** Classes must be complete or they are not included in the production CSS. */
private fun Deployment.asBorderColor() = when {
  isProduction -> "border-b-red-500"
  isStaging -> "border-b-green-500"
  isTest -> "border-b-white"
  else -> "border-b-blue-500"
}

private fun TagConsumer<*>.NavMenu(menuSections: List<MenuSection>) {
  if (menuSections.isNotEmpty()) {
    nav("flex flex-1 flex-col") {
      menuSections.mapIndexed { index, section ->
        div {
          val sectionSpacingStyle = if (index == 0) "" else "pt-6"
          div("text-xs font-semibold leading-6 text-gray-400 $sectionSpacingStyle") { +section.title }
          ul("flex flex-1 flex-col gap-y-7") {
            role = "list"

            li {
              section.links.sortedBy { it.label }.map { link ->
                ul("-mx-2 py-1") {
                  role = "list"
                  li {
                    // +"""<!-- Current: "bg-gray-800 text-white", Default: "text-gray-400 hover:text-white hover:bg-gray-800" -->"""
                    val isSelectedStyles = if (link.isSelected) {
                      "bg-gray-800 text-white"
                    } else {
                      "text-gray-400 hover:text-white hover:bg-gray-800"
                    }
                    link.rawHtml?.let {
                      div("group flex gap-x-3 rounded-md p-2 text-sm leading-6 font-semibold text-gray-400 hover:text-white hover:bg-gray-800") {
                        unsafe { +it }
                      }
                    } ?: let {
                      a(classes = "$isSelectedStyles group flex justify-between gap-x-3 rounded-md p-2 text-sm leading-6 font-semibold") {
                        href = link.href
                        link.hoverText?.let { title = it }

                        if (link.dataTurbo == true) {
                          attributes["data-turbo-preload"] = ""
                        } else if (link.dataTurbo == false) {
                          attributes["data-turbo"] = "false"
                        }

                        if (link.isPageNavigation) {
                          attributes["target"] = "_top"
                        } else if (link.openInNewTab) {
                          attributes["target"] = "_blank"
                        }

                        +link.label

                        if (link.openInNewTab) {
                          heroicon(Heroicons.MINI_ARROW_TOP_RIGHT_ON_SQUARE)
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
    }
  }
}
