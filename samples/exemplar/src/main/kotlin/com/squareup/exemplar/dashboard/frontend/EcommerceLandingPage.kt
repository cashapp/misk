package com.squareup.exemplar.dashboard.frontend

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.a
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.footer
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.header
import kotlinx.html.id
import kotlinx.html.img
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.nav
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.role
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.ul
import kotlinx.html.unsafe
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
class EcommerceLandingPage
@Inject
constructor(@AppName private val appName: String, private val deployment: Deployment) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String {
    return buildHtml {
      HtmlLayout(
        appRoot = "/app",
        title = "$appName frontend",
        playCdn = deployment.isLocalDevelopment,
        appCssPath = "/static/cache/tailwind.exemplar.min.css",
      ) {
        turbo_frame(id = "tab") {
          div("bg-white") {
            //            +"""<!--
            //    Mobile menu
            //
            //    Off-canvas menu for mobile, show/hide based on off-canvas menu state.
            //  -->"""
            div("relative z-40 lg:hidden") {
              role = "dialog"
              attributes["aria-modal"] = "true"
              //              +"""<!--
              //      Off-canvas menu backdrop, show/hide based on off-canvas menu state.
              //
              //      Entering: "transition-opacity ease-linear duration-300"
              //        From: "opacity-0"
              //        To: "opacity-100"
              //      Leaving: "transition-opacity ease-linear duration-300"
              //        From: "opacity-100"
              //        To: "opacity-0"
              //    -->"""
              div("fixed inset-0 bg-black bg-opacity-25") {}
              div("fixed inset-0 z-40 flex") {
                //                +"""<!--
                //        Off-canvas menu, show/hide based on off-canvas menu state.
                //
                //        Entering: "transition ease-in-out duration-300 transform"
                //          From: "-translate-x-full"
                //          To: "translate-x-0"
                //        Leaving: "transition ease-in-out duration-300 transform"
                //          From: "translate-x-0"
                //          To: "-translate-x-full"
                //      -->"""
                div("relative flex w-full max-w-xs flex-col overflow-y-auto bg-white pb-12 shadow-xl") {
                  div("flex px-4 pb-2 pt-5") {
                    button(classes = "-m-2 inline-flex items-center justify-center rounded-md p-2 text-gray-400") {
                      type = ButtonType.button
                      span("sr-only") { +"""Close menu""" }
                      unsafe {
                        """
                        <svg class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                        </svg>
                        """
                          .trimIndent()
                      }
                    }
                  }
                  //                  +"""<!-- Links -->"""
                  div("mt-2") {
                    div("border-b border-gray-200") {
                      div("-mb-px flex space-x-8 px-4") {
                        attributes["aria-orientation"] = "horizontal"
                        role = "tablist"
                        //                        +"""<!-- Selected: "border-indigo-600 text-indigo-600", Not Selected:
                        // "border-transparent text-gray-900" -->"""
                        button(
                          classes =
                            "border-transparent text-gray-900 flex-1 whitespace-nowrap border-b-2 px-1 py-4 text-base font-medium"
                        ) {
                          id = "tabs-1-tab-1"
                          attributes["aria-controls"] = "tabs-1-panel-1"
                          role = "tab"
                          type = ButtonType.button
                          +"""Women"""
                        }
                        //                        +"""<!-- Selected: "border-indigo-600 text-indigo-600", Not Selected:
                        // "border-transparent text-gray-900" -->"""
                        button(
                          classes =
                            "border-transparent text-gray-900 flex-1 whitespace-nowrap border-b-2 px-1 py-4 text-base font-medium"
                        ) {
                          id = "tabs-1-tab-2"
                          attributes["aria-controls"] = "tabs-1-panel-2"
                          role = "tab"
                          type = ButtonType.button
                          +"""Men"""
                        }
                      }
                    }
                    //                    +"""<!-- 'Women' tab panel, show/hide based on tab state. -->"""
                    div("space-y-12 px-4 py-6") {
                      id = "tabs-1-panel-1"
                      attributes["aria-labelledby"] = "tabs-1-tab-1"
                      role = "tabpanel"
                      attributes["tabindex"] = "0"
                      div("grid grid-cols-2 gap-x-4 gap-y-10") {
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-category-01.jpg"
                              alt = "Models sitting back to back, wearing Basic Tee in black and bone."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""New Arrivals"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-category-02.jpg"
                              alt = "Close up of Basic Tee fall bundle with off-white, ochre, olive, and black tees."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Basic Tees"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-category-03.jpg"
                              alt = "Model wearing minimalist watch with black wristband and white watch face."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Accessories"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-category-04.jpg"
                              alt = "Model opening tan leather long wallet with credit card pockets and cash pouch."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Carry"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                      }
                    }
                    //                    +"""<!-- 'Men' tab panel, show/hide based on tab state. -->"""
                    div("space-y-12 px-4 py-6") {
                      id = "tabs-1-panel-2"
                      attributes["aria-labelledby"] = "tabs-1-tab-2"
                      role = "tabpanel"
                      attributes["tabindex"] = "0"
                      div("grid grid-cols-2 gap-x-4 gap-y-10") {
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-01-men-category-01.jpg"
                              alt = "Hats and sweaters on wood shelves next to various colors of t-shirts on hangers."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""New Arrivals"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-01-men-category-02.jpg"
                              alt = "Model wearing light heather gray t-shirt."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Basic Tees"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-01-men-category-03.jpg"
                              alt =
                                "Grey 6-panel baseball hat with black brim, black mountain graphic on front, and light heather gray body."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Accessories"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                        div("group relative") {
                          div("aspect-h-1 aspect-w-1 overflow-hidden rounded-md bg-gray-100 group-hover:opacity-75") {
                            img(classes = "object-cover object-center") {
                              src = "https://tailwindui.com/img/ecommerce-images/mega-menu-01-men-category-04.jpg"
                              alt =
                                "Model putting folded cash into slim card holder olive leather wallet with hand stitching."
                            }
                          }
                          a(classes = "mt-6 block text-sm font-medium text-gray-900") {
                            href = "#"
                            span("absolute inset-0 z-10") { attributes["aria-hidden"] = "true" }
                            +"""Carry"""
                          }
                          p("mt-1 text-sm text-gray-500") {
                            attributes["aria-hidden"] = "true"
                            +"""Shop now"""
                          }
                        }
                      }
                    }
                  }
                  div("space-y-6 border-t border-gray-200 px-4 py-6") {
                    div("flow-root") {
                      a(classes = "-m-2 block p-2 font-medium text-gray-900") {
                        href = "#"
                        +"""Company"""
                      }
                    }
                    div("flow-root") {
                      a(classes = "-m-2 block p-2 font-medium text-gray-900") {
                        href = "#"
                        +"""Stores"""
                      }
                    }
                  }
                  div("space-y-6 border-t border-gray-200 px-4 py-6") {
                    div("flow-root") {
                      a(classes = "-m-2 block p-2 font-medium text-gray-900") {
                        href = "#"
                        +"""Create an account"""
                      }
                    }
                    div("flow-root") {
                      a(classes = "-m-2 block p-2 font-medium text-gray-900") {
                        href = "#"
                        +"""Sign in"""
                      }
                    }
                  }
                  div("space-y-6 border-t border-gray-200 px-4 py-6") {
                    //                    +"""<!-- Currency selector -->"""
                    form {
                      div("inline-block") {
                        label("sr-only") {
                          htmlFor = "mobile-currency"
                          +"""Currency"""
                        }
                        div(
                          "group relative -ml-2 rounded-md border-transparent focus-within:ring-2 focus-within:ring-white"
                        ) {
                          select(
                            "flex items-center rounded-md border-transparent bg-none py-0.5 pl-2 pr-5 text-sm font-medium text-gray-700 focus:border-transparent focus:outline-none focus:ring-0 group-hover:text-gray-800"
                          ) {
                            id = "mobile-currency"
                            name = "currency"
                            option { +"""CAD""" }
                            option { +"""USD""" }
                            option { +"""AUD""" }
                            option { +"""EUR""" }
                            option { +"""GBP""" }
                          }
                          div("pointer-events-none absolute inset-y-0 right-0 flex items-center") {
                            unsafe {
                              """
                              <svg class="h-5 w-5 text-gray-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                              </svg>
                              """
                                .trimIndent()
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            //            +"""<!-- Hero section -->"""
            div("relative bg-gray-900") {
              //              +"""<!-- Decorative image and overlay -->"""
              div("absolute inset-0 overflow-hidden") {
                attributes["aria-hidden"] = "true"
                img(classes = "h-full w-full object-cover object-center") {
                  src = "https://tailwindui.com/img/ecommerce-images/home-page-01-hero-full-width.jpg"
                  alt = ""
                }
              }
              div("absolute inset-0 bg-gray-900 opacity-50") { attributes["aria-hidden"] = "true" }
              //              +"""<!-- Navigation -->"""
              header("relative z-10") {
                nav {
                  attributes["aria-label"] = "Top"
                  //                  +"""<!-- Top navigation -->"""
                  div("bg-gray-900") {
                    div("mx-auto flex h-10 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8") {
                      //                      +"""<!-- Currency selector -->"""
                      form {
                        div {
                          label("sr-only") {
                            htmlFor = "desktop-currency"
                            +"""Currency"""
                          }
                          div(
                            "group relative -ml-2 rounded-md border-transparent bg-gray-900 focus-within:ring-2 focus-within:ring-white"
                          ) {
                            select(
                              "flex items-center rounded-md border-transparent bg-gray-900 bg-none py-0.5 pl-2 pr-5 text-sm font-medium text-white focus:border-transparent focus:outline-none focus:ring-0 group-hover:text-gray-100"
                            ) {
                              id = "desktop-currency"
                              name = "currency"
                              option { +"""CAD""" }
                              option { +"""USD""" }
                              option { +"""AUD""" }
                              option { +"""EUR""" }
                              option { +"""GBP""" }
                            }
                            div("pointer-events-none absolute inset-y-0 right-0 flex items-center") {
                              unsafe {
                                """
                                <svg class="h-5 w-5 text-gray-300" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                                  <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
                                </svg>
                                """
                                  .trimIndent()
                              }
                            }
                          }
                        }
                      }
                      div("flex items-center space-x-6") {
                        a(classes = "text-sm font-medium text-white hover:text-gray-100") {
                          href = "#"
                          +"""Sign in"""
                        }
                        a(classes = "text-sm font-medium text-white hover:text-gray-100") {
                          href = "#"
                          +"""Create an account"""
                        }
                      }
                    }
                  }
                }
              }
              div("relative mx-auto flex max-w-3xl flex-col items-center px-6 py-32 text-center sm:py-64 lg:px-0") {
                h1("text-4xl font-bold tracking-tight text-white lg:text-6xl") { +"""New arrivals are here""" }
                p("mt-4 text-xl text-white") {
                  +"""The new arrivals have, well, newly arrived. Check out the latest options from our summer small-batch release while they're still in stock."""
                }
                a(
                  classes =
                    "mt-8 inline-block rounded-md border border-transparent bg-white px-8 py-3 text-base font-medium text-gray-900 hover:bg-gray-100"
                ) {
                  href = "#"
                  +"""Shop New Arrivals"""
                }
              }
            }
            footer("bg-gray-900") {
              attributes["aria-labelledby"] = "footer-heading"
              h2("sr-only") {
                id = "footer-heading"
                +"""Footer"""
              }
              div("mx-auto max-w-7xl px-4 sm:px-6 lg:px-8") {
                div("py-20 xl:grid xl:grid-cols-3 xl:gap-8") {
                  div("grid grid-cols-2 gap-8 xl:col-span-2") {
                    div("space-y-12 md:grid md:grid-cols-2 md:gap-8 md:space-y-0") {
                      div {
                        h3("text-sm font-medium text-white") { +"""Shop""" }
                        ul("mt-6 space-y-6") {
                          role = "list"
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Bags"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Tees"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Objects"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Home Goods"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Accessories"""
                            }
                          }
                        }
                      }
                      div {
                        h3("text-sm font-medium text-white") { +"""Company""" }
                        ul("mt-6 space-y-6") {
                          role = "list"
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Who we are"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Sustainability"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Press"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Careers"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Terms &amp; Conditions"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Privacy"""
                            }
                          }
                        }
                      }
                    }
                    div("space-y-12 md:grid md:grid-cols-2 md:gap-8 md:space-y-0") {
                      div {
                        h3("text-sm font-medium text-white") { +"""Account""" }
                        ul("mt-6 space-y-6") {
                          role = "list"
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Manage Account"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Returns &amp; Exchanges"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Redeem a Gift Card"""
                            }
                          }
                        }
                      }
                      div {
                        h3("text-sm font-medium text-white") { +"""Connect""" }
                        ul("mt-6 space-y-6") {
                          role = "list"
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Contact Us"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Twitter"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Instagram"""
                            }
                          }
                          li("text-sm") {
                            a(classes = "text-gray-300 hover:text-white") {
                              href = "#"
                              +"""Pinterest"""
                            }
                          }
                        }
                      }
                    }
                  }
                  div("mt-12 md:mt-16 xl:mt-0") {
                    h3("text-sm font-medium text-white") { +"""Sign up for our newsletter""" }
                    p("mt-6 text-sm text-gray-300") { +"""The latest deals and savings, sent to your inbox weekly.""" }
                    form(classes = "mt-2 flex sm:max-w-md") {
                      label("sr-only") {
                        htmlFor = "email-address"
                        +"""Email address"""
                      }
                      input(
                        classes =
                          "w-full min-w-0 appearance-none rounded-md border border-white bg-white px-4 py-2 text-base text-gray-900 placeholder-gray-500 shadow-sm focus:border-white focus:outline-none focus:ring-2 focus:ring-white focus:ring-offset-2 focus:ring-offset-gray-900"
                      ) {
                        id = "email-address"
                        type = InputType.text
                        attributes["autocomplete"] = "email"
                        required = true
                      }
                      div("ml-4 flex-shrink-0") {
                        button(
                          classes =
                            "flex w-full items-center justify-center rounded-md border border-transparent bg-indigo-600 px-4 py-2 text-base font-medium text-white shadow-sm hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 focus:ring-offset-gray-900"
                        ) {
                          type = ButtonType.submit
                          +"""Sign up"""
                        }
                      }
                    }
                  }
                }
                div("border-t border-gray-800 py-10") {
                  p("text-sm text-gray-400") { +"""Copyright &copy; 2021 Your Company, Inc.""" }
                }
              }
            }
          }
        }
      }
    }
  }

  companion object {
    const val PATH = "/ui/example/ecommerce-landing-page"
  }
}
