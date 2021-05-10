package misk.cloud.aws

/**
 * Soft deprecation - use wisp.aws.environment
 */

/** [AwsRegion] is the region in which the service is running */
data class AwsRegion(val name: String)

fun wisp.aws.environment.AwsRegion.toMiskAwsRegion(): AwsRegion = AwsRegion(this.name)

/** [AwsAccountId] is the id of the account in which the service is running */
data class AwsAccountId(val value: String)

fun wisp.aws.environment.AwsAccountId.toMiskAwsAccountId(): AwsAccountId = AwsAccountId(this.value)
