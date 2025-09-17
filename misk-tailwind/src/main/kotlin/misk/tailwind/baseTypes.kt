package misk.tailwind

/**
 * Defines a link (such as for <a href />) with configuration including for styles and Hotwire Turbo handling.
 */
data class Link @JvmOverloads constructor(
  val label: String,
  val href: String,
  val style: Style? = null,
  val isSelected: Boolean = false,
  /** This forces page navigation vs within Turbo Frame navigation by adding a target="_top" attribute. */
  val isPageNavigation: Boolean = false,
  /**
   * null: default
   * true: preload
   * false: disabled
   */
  val dataTurbo: Boolean? = true,
  val openInNewTab: Boolean = false,
  val rawHtml: String? = null,
  val hoverText: String? = null,
  val dataAction: String? = null,
  val onClick: String? = null,
)

interface Style {
  val classes: String
}
