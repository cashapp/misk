package misk.aws2.dynamodb.testing

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class LocalDynamoDb : wisp.aws2.dynamodb.testing.LocalDynamoDb {
  @Inject constructor() : super()
  constructor(port: Int) : super(port)

  companion object {
    internal fun pickPort() = wisp.aws2.dynamodb.testing.LocalDynamoDb.pickPort()
  }

}
