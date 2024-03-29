package misk.web.dashboard

/** Per dashboard customization of the navbar theme */
@Deprecated("DashboardTheme is no longer supported in the new v2 dashboard and will be removed")
data class DashboardTheme(
  val dashboard_slug: String,
  val theme: MiskWebTheme,
)

@Deprecated("DashboardTheme is no longer supported in the new v2 dashboard and will be removed")
inline fun <reified DA : Annotation> DashboardTheme(
  theme: MiskWebTheme
) = DashboardTheme(
  dashboard_slug = ValidWebEntry.slugify<DA>(),
  theme = theme
)


/**
 * Matches the Misk-Web Theme interface  in @misk/core/src/utilities/theme.ts
 */
@Deprecated("MiskWebTheme is no longer supported in the new v2 dashboard and will be removed")
data class MiskWebTheme(
  val bannerLinkHover: String,
  val bannerText: String,
  val button: String,
  val buttonHover: String,
  val categoryText: String,
  val environmentToColor: EnvironmentToColorLookup,
  val navbarBackground: String,
  val navbarLinkHover: String,
  val navbarText: String,
) {
  companion object {
    val DEFAULT_THEME = MiskWebTheme(
      bannerLinkHover = MiskWebColor.WHITE.hexColor,
      bannerText = MiskWebColor.ACCENT.hexColor,
      button = MiskWebColor.GRAY.hexColor,
      buttonHover = MiskWebColor.WHITE.hexColor,
      categoryText = MiskWebColor.GRAY.hexColor,
      environmentToColor = EnvironmentToColorLookup(
        default = MiskWebColor.CADET.hexColor,
        DEVELOPMENT = MiskWebColor.BLUE.hexColor,
        TESTING = MiskWebColor.INDIGO.hexColor,
        STAGING = MiskWebColor.GREEN.hexColor,
        PRODUCTION = MiskWebColor.RED.hexColor
      ),
      navbarBackground = MiskWebColor.CADET.hexColor,
      navbarLinkHover = MiskWebColor.WHITE.hexColor,
      navbarText = MiskWebColor.PLATINUM.hexColor,
    )
  }
}

@Deprecated("EnvironmentToColorLookup is no longer supported in the new v2 dashboard and will be removed")
data class EnvironmentToColorLookup(
  val default: String,
  val DEVELOPMENT: String,
  val TESTING: String,
  val STAGING: String,
  val PRODUCTION: String,
)
