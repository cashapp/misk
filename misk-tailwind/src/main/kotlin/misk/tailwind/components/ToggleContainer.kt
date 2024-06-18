package misk.tailwind.components

import kotlinx.html.ButtonType
import kotlinx.html.TagConsumer
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.section
import kotlinx.html.span
import misk.tailwind.icons.Heroicons
import misk.tailwind.icons.heroicon

fun TagConsumer<*>.ToggleContainer(buttonText: String, borderless: Boolean = false, menuBlock: TagConsumer<*>.() -> Unit = {}, isOpen: Boolean = false, hiddenBlock: TagConsumer<*>.() -> Unit) {
  val borderStyle = if (!borderless) "border-b border-t border-gray-200" else ""
  val containerBorderStyle = if (!borderless) "border-t border-gray-200" else ""
  val hiddenStyle = if (isOpen) "" else "hidden"

  section("grid items-center $borderStyle") {
    attributes["data-controller"] = "toggle"
    attributes["aria-labelledby"] = "info-heading"

    div("relative col-start-1 row-start-1") {
      button(classes = "w-full font-medium text-gray-700 py-4") {
        attributes["data-action"] = "toggle#toggle"

        type = ButtonType.button
        attributes["aria-controls"] = "toggle-container-1"
        attributes["aria-expanded"] = "false"

        div("justify-between mx-auto flex space-x-6 divide-x divide-gray-200 text-sm text-left px-4") {
          menuBlock()

          div(classes = "pl-6") {
            div(classes = "group flex items-center font-medium text-gray-700") {
              span("pr-4") {
                +buttonText
              }

              // Toggle icon on click
              div("") {
                attributes["data-toggle-target"] = "toggleable"
                attributes["data-css-class"] = "hidden"
                heroicon(Heroicons.MINI_CHEVRON_DOWN)
              }
              div("hidden") {
                attributes["data-toggle-target"] = "toggleable"
                attributes["data-css-class"] = "hidden"
                heroicon(Heroicons.MINI_CHEVRON_UP)
              }
            }
          }
        }
      }
    }

    // TODO have this block respect narrower width for mobile screens
    div("$containerBorderStyle $hiddenStyle px-4") {
      attributes["data-toggle-target"] = "toggleable"
      attributes["data-css-class"] = "hidden"

      id = "toggle-container-1"

      hiddenBlock()
    }
  }
}
