package misk.exceptions.dynamodb

import com.amazonaws.http.timers.client.ClientExecutionTimeoutException
import com.amazonaws.services.dynamodbv2.model.TransactionCanceledException
import misk.inject.KInstallOnceModule
import misk.web.exceptions.ExceptionMapperModule

class DynamoDbExceptionMapperModule : KInstallOnceModule() {
  override fun configure() {
    install(ExceptionMapperModule.create<ClientExecutionTimeoutException, ClientExecutionTimeoutExceptionMapper>())
    install(ExceptionMapperModule.create<TransactionCanceledException, TransactionCanceledExceptionMapper>())
  }
}
