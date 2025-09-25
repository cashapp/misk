package wisp.config

@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "Config()",
    imports = ["misk.config.Config"]
  )
)
interface Config
