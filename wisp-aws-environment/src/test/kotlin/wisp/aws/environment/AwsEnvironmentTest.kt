package wisp.aws.environment

import org.junit.jupiter.api.Test
import wisp.deployment.FakeEnvironmentVariableLoader

internal class AwsEnvironmentTest {

  @Test
  fun `AWS Region should be the default when no environment variables set`() {
    val result = AwsEnvironment.awsRegion(
      FakeEnvironmentVariableLoader(emptyMap())
    )
    kotlin.test.assertEquals(defaultAwsRegion, result)
  }

  @Test
  fun `AWS Region should be the supplied default when no environment variables set`() {
    val result = AwsEnvironment.awsRegion(
      FakeEnvironmentVariableLoader(emptyMap()),
      defaultAwsRegion = "ap-east-1"
    )
    kotlin.test.assertEquals(AwsRegion("ap-east-1"), result)
  }

  @Test
  fun `AWS Region should be US_EAST_1 when REGION environment variable set to us-east-1`() {
    val result = AwsEnvironment.awsRegion(
      FakeEnvironmentVariableLoader(
        mapOf(
          "REGION" to "us-east-1"
        )
      )
    )
    kotlin.test.assertEquals(AwsRegion("us-east-1"), result)
  }

  @Test
  fun `AWS Region should be US_EAST_2 when AWS_REGION environment variable set to us-east-2`() {
    val result = AwsEnvironment.awsRegion(
      FakeEnvironmentVariableLoader(
        mapOf(
          "AWS_REGION" to "us-east-2"
        )
      )
    )
    kotlin.test.assertEquals(AwsRegion("us-east-2"), result)
  }

  @Test
  fun `AWS Region should be US_EAST_2 when MY_REGION environment variable set to us-east-2`() {
    val myRegion = "MY_REGION"
    val result = AwsEnvironment.awsRegion(
      FakeEnvironmentVariableLoader(
        mapOf(
          myRegion to "us-east-2"
        )
      ),
      listOf(myRegion)
    )
    kotlin.test.assertEquals(AwsRegion("us-east-2"), result)
  }

  companion object {
    val defaultAwsRegion = AwsRegion("us-west-2")
  }
}
