package misk.cloud.aws

import misk.inject.KAbstractModule
import java.lang.System.getenv

/** [AwsEnvironmentModule] pulls region and account information from installed env vars */
class AwsEnvironmentModule(
  private val region: String = getRequiredEnv("REGION"),
  private val accountId: String = getRequiredEnv("ACCOUNT_ID")
) : KAbstractModule() {
  override fun configure() {
    bind<AwsRegion>().toInstance(AwsRegion(region))
    bind<AwsAccountId>().toInstance(AwsAccountId(accountId))
  }

  companion object {
    private fun getRequiredEnv(name: String): String {
      return getenv(name) ?: throw IllegalStateException("$name env var not set")
    }
  }
}
