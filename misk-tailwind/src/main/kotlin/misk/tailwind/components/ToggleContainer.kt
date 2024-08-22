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

fun TagConsumer<*>.ToggleContainer(buttonText: String, classes: String = "", borderless: Boolean = false, marginless: Boolean = false, menuBlock: TagConsumer<*>.() -> Unit = {}, isOpen: Boolean = false, hiddenBlock: TagConsumer<*>.() -> Unit) {
  val borderStyle = if (!borderless) "border-b border-t border-gray-200" else ""
  val containerBorderStyle = if (!borderless) "border-t border-gray-200" else ""
  val hiddenStyle = if (isOpen) "" else "hidden"

  section("grid items-center $borderStyle $classes") {
    attributes["data-controller"] = "toggle"
    attributes["aria-labelledby"] = "info-heading"

    div {
      val buttonStyle = if (marginless) "" else "py-4"
      button(classes = "w-full font-medium text-gray-700 $buttonStyle") {
        attributes["data-action"] = "toggle#toggle"

        type = ButtonType.button
        attributes["aria-controls"] = "toggle-container-button"
        attributes["aria-expanded"] = "false"

        val marginStyle = if (marginless) "" else "space-x-6 px-4"
        div("justify-between mx-auto flex $marginStyle divide-x divide-gray-200 text-sm text-left") {
          menuBlock()

          val leftPadStyle = if (marginless) "" else "pl-6"
          div(classes = leftPadStyle) {
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

      id = "toggle-container-contents"

      hiddenBlock()
    }
  }
}
