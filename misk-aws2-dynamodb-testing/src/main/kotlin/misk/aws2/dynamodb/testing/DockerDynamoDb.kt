package misk.aws2.dynamodb.testing

import misk.testing.ExternalDependency

@Deprecated(message = "Not longer needed. Please remove reference.")
object DockerDynamoDb : ExternalDependency {
  override fun beforeEach() {
    // noop
  }

  override fun afterEach() {
    // noop
  }

  override fun startup() {
    // noop
  }

  override fun shutdown() {
    // noop
  }
}
