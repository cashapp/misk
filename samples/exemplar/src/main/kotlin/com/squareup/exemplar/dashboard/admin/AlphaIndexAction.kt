package com.squareup.exemplar.dashboard.admin

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.html.ButtonType
import kotlinx.html.InputType
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.legend
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.textArea
import misk.web.Get
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.dashboard.AdminDashboardAccess
import misk.web.mediatype.MediaTypes
import misk.web.v2.DashboardPageLayout

@Singleton
class AlphaIndexAction @Inject constructor(private val dashboardPageLayout: DashboardPageLayout) : WebAction {
  @Get(PATH)
  @ResponseContentType(MediaTypes.TEXT_HTML)
  @AdminDashboardAccess
  fun get(): String =
    dashboardPageLayout.newBuilder().build { _, _, _ ->
      div("center container p-8") {
        form {
          div("space-y-12") {
            div("border-b border-gray-900/10 pb-12") {
              h1("text-base font-bold leading-7 text-gray-900") { +"""Alpha Example Admin Tool""" }
              h2("text-base font-semibold leading-7 text-gray-900") { +"""Profile""" }
              p("mt-1 text-sm leading-6 text-gray-600") {
                +"""This information will be displayed publicly so be careful what you share."""
              }
              div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                div("sm:col-span-4") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "username"
                    +"""Username"""
                  }
                  div("mt-2") {
                    div(
                      "flex rounded-md shadow-sm ring-1 ring-inset ring-gray-300 focus-within:ring-2 focus-within:ring-inset focus-within:ring-indigo-600 sm:max-w-md"
                    ) {
                      span("flex select-none items-center pl-3 text-gray-500 sm:text-sm") { +"""workcation.com/""" }
                      input(
                        classes =
                          "block flex-1 border-0 bg-transparent py-1.5 pl-1 text-gray-900 placeholder:text-gray-400 focus:ring-0 sm:text-sm sm:leading-6"
                      ) {
                        type = InputType.text
                        name = "username"
                        id = "username"
                        attributes["autocomplete"] = "username"
                        placeholder = "janesmith"
                      }
                    }
                  }
                }
                div("col-span-full") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "about"
                    +"""About"""
                  }
                  div("mt-2") {
                    textArea(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      id = "about"
                      name = "about"
                      rows = "3"
                    }
                  }
                  p("mt-3 text-sm leading-6 text-gray-600") { +"""Write a few sentences about yourself.""" }
                }
                div("col-span-full") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "photo"
                    +"""Photo"""
                  }
                  div("mt-2 flex items-center gap-x-3") {
                    //                    svg("h-12 w-12 text-gray-300") {
                    //                      viewbox = "0 0 24 24"
                    //                      fill = "currentColor"
                    //                      attributes["aria-hidden"] = "true"
                    //                      path {
                    //                        attributes["fill-rule"] = "evenodd"
                    //                        d =
                    //                          "M18.685 19.097A9.723 9.723 0 0021.75
                    // 12c0-5.385-4.365-9.75-9.75-9.75S2.25 6.615 2.25 12a9.723 9.723 0 003.065 7.097A9.716 9.716 0 0012
                    // 21.75a9.716 9.716 0 006.685-2.653zm-12.54-1.285A7.486 7.486 0 0112 15a7.486 7.486 0 015.855
                    // 2.812A8.224 8.224 0 0112 20.25a8.224 8.224 0 01-5.855-2.438zM15.75 9a3.75 3.75 0 11-7.5 0 3.75
                    // 3.75 0 017.5 0z"
                    //                        attributes["clip-rule"] = "evenodd"
                    //                      }
                    //                    }
                    button(
                      classes =
                        "rounded-md bg-white px-2.5 py-1.5 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
                    ) {
                      type = ButtonType.button
                      +"""Change"""
                    }
                  }
                }
                div("col-span-full") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "cover-photo"
                    +"""Cover photo"""
                  }
                  div("mt-2 flex justify-center rounded-lg border border-dashed border-gray-900/25 px-6 py-10") {
                    div("text-center") {
                      //                      svg("mx-auto h-12 w-12 text-gray-300") {
                      //                        viewbox = "0 0 24 24"
                      //                        fill = "currentColor"
                      //                        attributes["aria-hidden"] = "true"
                      //                        path {
                      //                          attributes["fill-rule"] = "evenodd"
                      //                          d =
                      //                            "M1.5 6a2.25 2.25 0 012.25-2.25h16.5A2.25 2.25 0 0122.5 6v12a2.25
                      // 2.25 0 01-2.25 2.25H3.75A2.25 2.25 0 011.5 18V6zM3 16.06V18c0 .414.336.75.75.75h16.5A.75.75 0
                      // 0021 18v-1.94l-2.69-2.689a1.5 1.5 0 00-2.12 0l-.88.879.97.97a.75.75 0 11-1.06
                      // 1.06l-5.16-5.159a1.5 1.5 0 00-2.12 0L3 16.061zm10.125-7.81a1.125 1.125 0 112.25 0 1.125 1.125 0
                      // 01-2.25 0z"
                      //                          attributes["clip-rule"] = "evenodd"
                      //                        }
                      //                      }
                      div("mt-4 flex text-sm leading-6 text-gray-600") {
                        label(
                          "relative cursor-pointer rounded-md bg-white font-semibold text-indigo-600 focus-within:outline-none focus-within:ring-2 focus-within:ring-indigo-600 focus-within:ring-offset-2 hover:text-indigo-500"
                        ) {
                          htmlFor = "file-upload"
                          span { +"""Upload a file""" }
                          input(classes = "sr-only") {
                            id = "file-upload"
                            name = "file-upload"
                            type = InputType.text
                          }
                        }
                        p("pl-1") { +"""or drag and drop""" }
                      }
                      p("text-xs leading-5 text-gray-600") { +"""PNG, JPG, GIF up to 10MB""" }
                    }
                  }
                }
              }
            }
            div("border-b border-gray-900/10 pb-12") {
              h2("text-base font-semibold leading-7 text-gray-900") { +"""Personal Information""" }
              p("mt-1 text-sm leading-6 text-gray-600") { +"""Use a permanent address where you can receive mail.""" }
              div("mt-10 grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6") {
                div("sm:col-span-3") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "first-name"
                    +"""First name"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "first-name"
                      id = "first-name"
                      attributes["autocomplete"] = "given-name"
                    }
                  }
                }
                div("sm:col-span-3") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "last-name"
                    +"""Last name"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "last-name"
                      id = "last-name"
                      attributes["autocomplete"] = "family-name"
                    }
                  }
                }
                div("sm:col-span-4") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "email"
                    +"""Email address"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      id = "email"
                      name = "email"
                      type = InputType.text
                      attributes["autocomplete"] = "email"
                    }
                  }
                }
                div("sm:col-span-3") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "country"
                    +"""Country"""
                  }
                  div("mt-2") {
                    select(
                      "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:max-w-xs sm:text-sm sm:leading-6"
                    ) {
                      id = "country"
                      name = "country"
                      attributes["autocomplete"] = "country-name"
                      option { +"""United States""" }
                      option { +"""Canada""" }
                      option { +"""Mexico""" }
                    }
                  }
                }
                div("col-span-full") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "street-address"
                    +"""Street address"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "street-address"
                      id = "street-address"
                      attributes["autocomplete"] = "street-address"
                    }
                  }
                }
                div("sm:col-span-2 sm:col-start-1") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "city"
                    +"""City"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "city"
                      id = "city"
                      attributes["autocomplete"] = "address-level2"
                    }
                  }
                }
                div("sm:col-span-2") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "region"
                    +"""State / Province"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "region"
                      id = "region"
                      attributes["autocomplete"] = "address-level1"
                    }
                  }
                }
                div("sm:col-span-2") {
                  label("block text-sm font-medium leading-6 text-gray-900") {
                    htmlFor = "postal-code"
                    +"""ZIP / Postal code"""
                  }
                  div("mt-2") {
                    input(
                      classes =
                        "block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    ) {
                      type = InputType.text
                      name = "postal-code"
                      id = "postal-code"
                      attributes["autocomplete"] = "postal-code"
                    }
                  }
                }
              }
            }
            div("border-b border-gray-900/10 pb-12") {
              h2("text-base font-semibold leading-7 text-gray-900") { +"""Notifications""" }
              p("mt-1 text-sm leading-6 text-gray-600") {
                +"""We'll always let you know about important changes, but you pick what else you want to hear about."""
              }
              div("mt-10 space-y-10") {
                div {
                  legend("text-sm font-semibold leading-6 text-gray-900") { +"""By Email""" }
                  div("mt-6 space-y-6") {
                    div("relative flex gap-x-3") {
                      div("flex h-6 items-center") {
                        input(classes = "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                          id = "comments"
                          name = "comments"
                          type = InputType.text
                        }
                      }
                      div("text-sm leading-6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "comments"
                          +"""Comments"""
                        }
                        p("text-gray-500") { +"""Get notified when someones posts a comment on a posting.""" }
                      }
                    }
                    div("relative flex gap-x-3") {
                      div("flex h-6 items-center") {
                        input(classes = "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                          id = "candidates"
                          name = "candidates"
                          type = InputType.text
                        }
                      }
                      div("text-sm leading-6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "candidates"
                          +"""Candidates"""
                        }
                        p("text-gray-500") { +"""Get notified when a candidate applies for a job.""" }
                      }
                    }
                    div("relative flex gap-x-3") {
                      div("flex h-6 items-center") {
                        input(classes = "h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                          id = "offers"
                          name = "offers"
                          type = InputType.text
                        }
                      }
                      div("text-sm leading-6") {
                        label("font-medium text-gray-900") {
                          htmlFor = "offers"
                          +"""Offers"""
                        }
                        p("text-gray-500") { +"""Get notified when a candidate accepts or rejects an offer.""" }
                      }
                    }
                  }
                }
                div {
                  legend("text-sm font-semibold leading-6 text-gray-900") { +"""Push Notifications""" }
                  p("mt-1 text-sm leading-6 text-gray-600") { +"""These are delivered via SMS to your mobile phone.""" }
                  div("mt-6 space-y-6") {
                    div("flex items-center gap-x-3") {
                      input(classes = "h-4 w-4 border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                        id = "push-everything"
                        name = "push-notifications"
                        type = InputType.text
                      }
                      label("block text-sm font-medium leading-6 text-gray-900") {
                        htmlFor = "push-everything"
                        +"""Everything"""
                      }
                    }
                    div("flex items-center gap-x-3") {
                      input(classes = "h-4 w-4 border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                        id = "push-email"
                        name = "push-notifications"
                        type = InputType.text
                      }
                      label("block text-sm font-medium leading-6 text-gray-900") {
                        htmlFor = "push-email"
                        +"""Same as email"""
                      }
                    }
                    div("flex items-center gap-x-3") {
                      input(classes = "h-4 w-4 border-gray-300 text-indigo-600 focus:ring-indigo-600") {
                        id = "push-nothing"
                        name = "push-notifications"
                        type = InputType.text
                      }
                      label("block text-sm font-medium leading-6 text-gray-900") {
                        htmlFor = "push-nothing"
                        +"""No push notifications"""
                      }
                    }
                  }
                }
              }
            }
          }
          div("mt-6 flex items-center justify-end gap-x-6") {
            button(classes = "text-sm font-semibold leading-6 text-gray-900") {
              type = ButtonType.button
              +"""Cancel"""
            }
            button(
              classes =
                "rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
            ) {
              type = ButtonType.submit
              +"""Save"""
            }
          }
        }
      }
    }

  companion object {
    const val PATH = "/_admin/exemplar/alpha/"
  }
}
