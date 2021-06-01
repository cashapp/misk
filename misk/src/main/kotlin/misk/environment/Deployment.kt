package misk.environment

/** Deployment describes the context in which the application is running */
//
// TODO(chrisryan): "soft" deprecating
// @Deprecated(
//  message = "Use wisp.deployment.Deployment",
//  replaceWith = ReplaceWith(
//    "Deployment",
//    "wisp.deployment.Deployment"
//  )
//)
class Deployment(
  name: String,
  isProduction: Boolean = false,
  isStaging: Boolean = false,
  isTest: Boolean = false,
  isLocalDevelopment: Boolean = false
) : wisp.deployment.Deployment(name, isProduction, isStaging, isTest, isLocalDevelopment)
