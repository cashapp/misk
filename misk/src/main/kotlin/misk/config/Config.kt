package misk.config

// TODO(chrisryan): soft deprecating...
// @Deprecated(
//  message = "Use wisp.config.Config directly",
//  replaceWith = ReplaceWith(
//    "Config",
//    "wisp.config.Config"
//  )
// )
@Deprecated("Use from misk-config instead")
interface Config : wisp.config.Config
