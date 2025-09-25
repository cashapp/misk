package wisp.aws.environment

/** [AwsRegion] is the region in which the service is running */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "AwsRegion()",
    imports = ["misk.cloud.aws.AwsRegion"]
  )
)
data class AwsRegion(val name: String)

/** [AwsAccountId] is the id of the account in which the service is running */
@Deprecated(
  message = "Duplicate implementations in Wisp are being migrated to the unified type in Misk.",
  replaceWith = ReplaceWith(
    expression = "AwsAccountId()",
    imports = ["misk.cloud.aws.AwsAccountId"]
  )
)
data class AwsAccountId(val value: String)
