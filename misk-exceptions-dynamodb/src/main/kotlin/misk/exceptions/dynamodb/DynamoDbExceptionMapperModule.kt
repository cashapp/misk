package misk.exceptions.dynamodb

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException
import misk.inject.KInstallOnceModule
import misk.web.exceptions.ExceptionMapperModule

@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Use the AWS SDK v2 DynamoDB module in " +
      "misk-aws2-dynamodb (misk.aws2.dynamodb.RealDynamoDbModule) instead."
)
class DynamoDbExceptionMapperModule : KInstallOnceModule() {
  override fun configure() {
    install(ExceptionMapperModule.create<ClientExecutionTimeoutException, ClientExecutionTimeoutExceptionMapper>())
    install(ExceptionMapperModule.create<TransactionCanceledException, TransactionCanceledExceptionMapper>())
  }
}
