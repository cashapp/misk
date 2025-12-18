package misk.tailwind.components

import kotlinx.html.TagConsumer
import kotlinx.html.a
import kotlinx.html.div
import kotlinx.html.p
import kotlinx.html.span
import kotlinx.html.unsafe
import misk.tailwind.Link

@Deprecated("Use AlertSuccess with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.AlertMessage(successMessage: String?, errorMessage: String?, label: String?, link: String?) =
  AlertMessage(
    successMessage = successMessage,
    errorMessage = errorMessage,
    link = label?.let { Link(label = label, href = link ?: "#") },
  )

fun TagConsumer<*>.AlertMessage(successMessage: String?, errorMessage: String?, link: Link?) {
  val isSuccessMessage = !successMessage.isNullOrBlank()
  val message = if (isSuccessMessage) successMessage else errorMessage

  when (isSuccessMessage) {
    true -> AlertSuccess(message, link)
    false -> AlertError(message, link)
  }
}

@Deprecated("Use Alert with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.Alert(
  theme: AlertTheme,
  message: String?,
  label: String?,
  link: String?,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) {
  Alert(
    theme = theme,
    message = message,
    link = label?.let { Link(label = label, href = link ?: "#") },
    spaceAbove = spaceAbove,
    spaceBelow = spaceBelow,
  )
}

fun TagConsumer<*>.Alert(
  theme: AlertTheme,
  message: String?,
  link: Link? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) {
  if (!message.isNullOrBlank()) {
    val topSpacer = if (spaceAbove) "mt-4" else ""
    val bottomSpacer = if (spaceBelow) "mb-4" else ""
    div("rounded-md ${theme.backgroundColor} p-4 $topSpacer $bottomSpacer") {
      div("flex") {
        div("flex-shrink-0") {
          when (theme) {
            AlertTheme.BLUE,
            AlertTheme.BLUE_HIGHLIGHT -> {
              unsafe {
                raw(
                  """
                    <svg class="h-5 w-5 ${theme.iconColor}" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                      <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a.75.75 0 000 1.5h.253a.25.25 0 01.244.304l-.459 2.066A1.75 1.75 0 0010.747 15H11a.75.75 0 000-1.5h-.253a.25.25 0 01-.244-.304l.459-2.066A1.75 1.75 0 009.253 9H9z" clip-rule="evenodd" />
                    </svg>
                      """
                    .trimIndent()
                )
              }
            }

            AlertTheme.GREEN -> {
              unsafe {
                raw(
                  """
                      <svg class="h-5 w-5 ${theme.iconColor}" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.857-9.809a.75.75 0 00-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 10-1.06 1.061l2.5 2.5a.75.75 0 001.137-.089l4-5.5z" clip-rule="evenodd" />
                      </svg>
                      """
                    .trimIndent()
                )
              }
            }

            AlertTheme.RED -> {
              unsafe {
                raw(
                  """
                      <svg class="h-5 w-5 ${theme.iconColor}" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                        <path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.28 7.22a.75.75 0 00-1.06 1.06L8.94 10l-1.72 1.72a.75.75 0 101.06 1.06L10 11.06l1.72 1.72a.75.75 0 101.06-1.06L11.06 10l1.72-1.72a.75.75 0 00-1.06-1.06L10 8.94 8.28 7.22z" clip-rule="evenodd" />
                      </svg>
                      """
                    .trimIndent()
                )
              }
            }
          }
        }

        div("ml-3 flex-1 md:flex md:justify-between") {
          p("text-sm ${theme.textColor}") { +message }
          p("mt-3 text-sm md:ml-6 md:mt-0") {
            a(classes = "whitespace-nowrap font-medium ${theme.textColor} hover:${theme.hoverTextColor}") {
              if (link?.href in listOf("/_admin/database/")) {
                // TODO Expose non-hardcoded configurability, this disables turbo for existing Misk-Web tabs so browser
                // history works as expected
                attributes["data-turbo"] = "false"
              }

              link?.let {
                href = link.href
                if (link.openInNewTab) {
                  target = "_blank"
                }
              }

              if (link?.isPageNavigation == true) {
                target = "_top"
              }

              link?.dataAction?.let {
                href = "#"
                attributes["data-action"] = it
              }

              link?.onClick?.let {
                href = "#"
                attributes["onclick"] = it
              }

              link?.label?.let {
                +it
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
  }
}

@Deprecated("Use AlertSuccess with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.AlertSuccess(
  message: String?,
  label: String? = null,
  link: String? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) =
  AlertSuccess(
    message = message,
    link = label?.let { Link(label = label, href = link ?: "#") },
    spaceAbove = spaceAbove,
    spaceBelow = spaceBelow,
  )

fun TagConsumer<*>.AlertSuccess(
  message: String?,
  link: Link? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) = Alert(AlertTheme.GREEN, message, link, spaceAbove, spaceBelow)

@Deprecated("Use AlertError with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.AlertError(
  message: String?,
  label: String? = null,
  link: String? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
  dataAction: String? = null,
  onClick: String? = null,
) =
  AlertError(
    message = message,
    link = label?.let { Link(label = label, href = link ?: "#", dataAction = dataAction, onClick = onClick) },
    spaceAbove = spaceAbove,
    spaceBelow = spaceBelow,
  )

fun TagConsumer<*>.AlertError(
  message: String?,
  link: Link? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) = Alert(AlertTheme.RED, message, link, spaceAbove, spaceBelow)

@Deprecated("Use AlertInfo with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.AlertInfo(
  message: String?,
  label: String? = null,
  link: String? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) =
  AlertInfo(
    message = message,
    link = label?.let { Link(label = label, href = link ?: "#") },
    spaceAbove = spaceAbove,
    spaceBelow = spaceBelow,
  )

fun TagConsumer<*>.AlertInfo(
  message: String?,
  link: Link? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) = Alert(AlertTheme.BLUE, message, link, spaceAbove, spaceBelow)

@Deprecated("Use AlertInfoHighlight with misk.tailwind.Link parameter instead.")
fun TagConsumer<*>.AlertInfoHighlight(
  message: String?,
  label: String? = null,
  link: String? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) =
  AlertInfoHighlight(
    message = message,
    link = label?.let { Link(label = label, href = link ?: "#") },
    spaceAbove = spaceAbove,
    spaceBelow = spaceBelow,
  )

fun TagConsumer<*>.AlertInfoHighlight(
  message: String?,
  link: Link? = null,
  spaceAbove: Boolean = false,
  spaceBelow: Boolean = true,
) = Alert(AlertTheme.BLUE_HIGHLIGHT, message, link, spaceAbove, spaceBelow)

enum class AlertTheme(
  val backgroundColor: String,
  val textColor: String,
  val headingTextColor: String,
  val iconColor: String,
  val hoverTextColor: String,
) {
  BLUE_HIGHLIGHT(
    backgroundColor = "bg-white",
    textColor = "text-blue-700",
    headingTextColor = "text-blue-800",
    iconColor = "text-blue-400",
    hoverTextColor = "text-blue-600",
  ),
  BLUE(
    backgroundColor = "bg-blue-50",
    textColor = "text-blue-700",
    headingTextColor = "text-blue-800",
    iconColor = "text-blue-400",
    hoverTextColor = "text-blue-600",
  ),
  GREEN(
    backgroundColor = "bg-green-50",
    textColor = "text-green-700",
    headingTextColor = "text-green-800",
    iconColor = "text-green-400",
    hoverTextColor = "text-green-600",
  ),
  RED(
    backgroundColor = "bg-red-50",
    textColor = "text-red-700",
    headingTextColor = "text-red-800",
    iconColor = "text-red-400",
    hoverTextColor = "text-red-600",
  ),
}
