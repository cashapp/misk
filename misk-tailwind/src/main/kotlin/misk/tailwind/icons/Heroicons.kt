package misk.tailwind.icons

import kotlinx.html.TagConsumer
import kotlinx.html.span
import kotlinx.html.unsafe

/**
 * kotlinx.html component to use the above [Heroicons] enum to inline SVG icons.
 *
 * ```
 * div {
 *   heroicon(Heroicons.MINI_CHEVRON_DOWN)
 *   heroicon(Heroicons.MINI_CHEVRON_DOWN, "text-gray-400 group-hover:text-gray-500")
 * }
 * ```
 *
 * @param modifierClass override of the icon's [defaultModifierClass]. For example if icon should
 *    have a different color, boldness...etc then a [modifierClass] can be provided which overrides
 *    the icon's [defaultModifierClass].
 */
fun TagConsumer<*>.heroicon(
  icon: Heroicons,
  modifierClass: String? = null,
  spanClass: String? = null
) {
  span(spanClass) {
    //    """<!-- Heroicon name: ${it.id} -->"""
    unsafe {
      raw(
        icon.rawHtml(
          icon.svgClass + " " + (HeroiconProps(
            icon,
            modifierClass,
            spanClass
          ).modifierClass ?: icon.defaultModifierClass)
        )
      )
    }
  }
}

data class HeroiconProps @JvmOverloads constructor(
  val icon: Heroicons,
  val modifierClass: String? = null,
  val spanClass: String? = null,
)

/**
 * Tailwind Heroicons Kotlin Bindings
 * https://heroicons.com/
 *
 * Add any new icons as they are used.
 */
enum class Heroicons(
  val id: String,
  val svgClass: String,
  val defaultModifierClass: String = "",
  val rawHtml: (String) -> String,
) {
  OUTLINE_CODE_BRACKET(
    id = "outline/code-bracket",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17.25 6.75 22.5 12l-5.25 5.25m-10.5 0L1.5 12l5.25-5.25m7.5-3-4.5 16.5" />
      </svg>
      """.trimIndent()
    }
  ),
  OUTLINE_CODE_BRACKET_SQUARE(
    id = "outline/code-bracket",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" d="M14.25 9.75 16.5 12l-2.25 2.25m-4.5 0L7.5 12l2.25-2.25M6 20.25h12A2.25 2.25 0 0 0 20.25 18V6A2.25 2.25 0 0 0 18 3.75H6A2.25 2.25 0 0 0 3.75 6v12A2.25 2.25 0 0 0 6 20.25Z" />
      </svg>
      """.trimIndent()
    }
  ),
  OUTLINE_ARROW_DOWN_ON_SQUARE(
    id = "outline/arrow-down-on-square",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 8.25H7.5a2.25 2.25 0 0 0-2.25 2.25v9a2.25 2.25 0 0 0 2.25 2.25h9a2.25 2.25 0 0 0 2.25-2.25v-9a2.25 2.25 0 0 0-2.25-2.25H15M9 12l3 3m0 0 3-3m-3 3V2.25" />
      </svg>
      """.trimIndent()
    }
  ),
  OUTLINE_ARROW_TOP_RIGHT_ON_SQUARE(
    id = "outline/arrow-top-right-on-square",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 6H5.25A2.25 2.25 0 0 0 3 8.25v10.5A2.25 2.25 0 0 0 5.25 21h10.5A2.25 2.25 0 0 0 18 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25" />
      </svg>

      """.trimIndent()
    }
  ),
  OUTLINE_XMARK(
    id = "outline/x-mark",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
         <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
      </svg>
      """.trimIndent()
    }
  ),
  OUTLINE_HOME(
    id = "outline/home",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
         <path stroke-linecap="round" stroke-linejoin="round" d="M2.25 12l8.954-8.955c.44-.439 1.152-.439 1.591 0L21.75 12M4.5 9.75v10.125c0 .621.504 1.125 1.125 1.125H9.75v-4.875c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125V21h4.125c.621 0 1.125-.504 1.125-1.125V9.75M8.25 21h8.25" />
      </svg>
    """.trimIndent()
    }
  ),
  OUTLINE_BARS_4(
    id = "outline/bars-4",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500 ",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 010 3.75H5.625a1.875 1.875 0 010-3.75z" />
      </svg>
    """.trimIndent()
    }
  ),
  OUTLINE_CLOCK(
    id = "outline/clock",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m5.231 13.481L15 17.25m-4.5-15H5.625c-.621 0-1.125.504-1.125 1.125v16.5c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9zm3.75 11.625a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
      </svg>
    """.trimIndent()
    }
  ),
  OUTLINE_DOCUMENT_MAGNIFYING_GLASS(
    id = "outline/document-magnifying-glass",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0M3.124 7.5A8.969 8.969 0 015.292 3m13.416 0a8.969 8.969 0 012.168 4.5" />
      </svg>
    """.trimIndent()
    }
  ),
  MINI_CHEVRON_UP_DOWN(
    id = "mini/chevron-up-down",
    svgClass = "h-5 w-5 flex-shrink-0",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
         <path fill-rule="evenodd" d="M10 3a.75.75 0 01.55.24l3.25 3.5a.75.75 0 11-1.1 1.02L10 4.852 7.3 7.76a.75.75 0 01-1.1-1.02l3.25-3.5A.75.75 0 0110 3zm-3.76 9.2a.75.75 0 011.06.04l2.7 2.908 2.7-2.908a.75.75 0 111.1 1.02l-3.25 3.5a.75.75 0 01-1.1 0l-3.25-3.5a.75.75 0 01.04-1.06z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    }
  ),
  OUTLINE_QUEUE_LIST(
    id = "queue-list",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 12h16.5m-16.5 3.75h16.5M3.75 19.5h16.5M5.625 4.5h12.75a1.875 1.875 0 010 3.75H5.625a1.875 1.875 0 010-3.75z" />
      </svg>
    """.trimIndent()
    }
  ),
  DOCUMENT_MAGNIFYING_GLASS(
    id = "document-magnifying-glass",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m5.231 13.481L15 17.25m-4.5-15H5.625c-.621 0-1.125.504-1.125 1.125v16.5c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9zm3.75 11.625a2.625 2.625 0 11-5.25 0 2.625 2.625 0 015.25 0z" />
      </svg>
    """.trimIndent()
    }
  ),
  BELL_ALERT(
    id = "bell-alert",
    svgClass = "mr-3 flex-shrink-0 h-5 w-5",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
         <path stroke-linecap="round" stroke-linejoin="round" d="M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0M3.124 7.5A8.969 8.969 0 015.292 3m13.416 0a8.969 8.969 0 012.168 4.5" />
      </svg>
    """.trimIndent()
    }
  ),
  OUTLINE_BARS_3_CENTER_LEFT(
    id = "outline/bars-3-center-left",
    svgClass = "h-6 w-6",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" aria-hidden="true">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12H12m-8.25 5.25h16.5" />
      </svg>
    """.trimIndent()
    }
  ),
  MAGNIFYING_GLASS(
    id = "magnifying-glass",
    svgClass = "h-5 w-5",
    defaultModifierClass = "text-gray-400",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor" class="w-6 h-6">
        <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_FUNNEL(
    id = "mini/funnel",
    svgClass = "mr-2 h-5 w-5 flex-none",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
         <path fill-rule="evenodd" d="M2.628 1.601C5.028 1.206 7.49 1 10 1s4.973.206 7.372.601a.75.75 0 01.628.74v2.288a2.25 2.25 0 01-.659 1.59l-4.682 4.683a2.25 2.25 0 00-.659 1.59v3.037c0 .684-.31 1.33-.844 1.757l-1.937 1.55A.75.75 0 018 18.25v-5.757a2.25 2.25 0 00-.659-1.591L2.659 6.22A2.25 2.25 0 012 4.629V2.34a.75.75 0 01.628-.74z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_CHEVRON_UP(
    id = "mini/chevron-up",
    // Maybe needs -mr-1 ml-1
    svgClass = "h-5 w-5 flex-shrink-0",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fill-rule="evenodd" d="M14.77 12.79a.75.75 0 01-1.06-.02L10 8.832 6.29 12.77a.75.75 0 11-1.08-1.04l4.25-4.5a.75.75 0 011.08 0l4.25 4.5a.75.75 0 01-.02 1.06z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_CHEVRON_DOWN(
    id = "mini/chevron-down",
    // Maybe needs -mr-1 ml-1
    svgClass = "h-5 w-5 flex-shrink-0",
    defaultModifierClass = "text-gray-400 group-hover:text-gray-500",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_ARROW_LONG_LEFT(
    id = "mini/arrow-long-left",
    svgClass = "mr-3 h-5 w-5",
    defaultModifierClass = "text-gray-400",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fill-rule="evenodd" d="M18 10a.75.75 0 01-.75.75H4.66l2.1 1.95a.75.75 0 11-1.02 1.1l-3.5-3.25a.75.75 0 010-1.1l3.5-3.25a.75.75 0 111.02 1.1l-2.1 1.95h12.59A.75.75 0 0118 10z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_ARROW_LONG_RIGHT(
    id = "mini/arrow-long-right",
    svgClass = "ml-3 h-5 w-5",
    defaultModifierClass = "text-gray-400",
    rawHtml = {
      """
      <svg class="$it" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path fill-rule="evenodd" d="M2 10a.75.75 0 01.75-.75h12.59l-2.1-1.95a.75.75 0 111.02-1.1l3.5 3.25a.75.75 0 010 1.1l-3.5 3.25a.75.75 0 11-1.02-1.1l2.1-1.95H2.75A.75.75 0 012 10z" clip-rule="evenodd" />
      </svg>
    """.trimIndent()
    },
  ),
  MINI_ARROW_TOP_RIGHT_ON_SQUARE(
    id = "mini/arrow-top-right-on-square",
    svgClass = "ml-3 h-5 w-5",
    defaultModifierClass = "text-gray-400",
    rawHtml = {
      """
      <svg class="w-5 h-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
        <path fill-rule="evenodd" d="M4.25 5.5a.75.75 0 00-.75.75v8.5c0 .414.336.75.75.75h8.5a.75.75 0 00.75-.75v-4a.75.75 0 011.5 0v4A2.25 2.25 0 0112.75 17h-8.5A2.25 2.25 0 012 14.75v-8.5A2.25 2.25 0 014.25 4h5a.75.75 0 010 1.5h-5z" clip-rule="evenodd" />
        <path fill-rule="evenodd" d="M6.194 12.753a.75.75 0 001.06.053L16.5 4.44v2.81a.75.75 0 001.5 0v-4.5a.75.75 0 00-.75-.75h-4.5a.75.75 0 000 1.5h2.553l-9.056 8.194a.75.75 0 00-.053 1.06z" clip-rule="evenodd" />
      </svg>

    """.trimIndent()
    },
  ),
}
