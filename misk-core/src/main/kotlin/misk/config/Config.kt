package misk.config

@Deprecated(
  message = "Use wisp.config.Config directly",
  replaceWith = ReplaceWith(
    "Config",
    "wisp.config.Config"
  )
)
interface Config : wisp.config.Config
