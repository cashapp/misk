package misk.dynamodb

import com.google.common.util.concurrent.Service

/**
 * Service that's running when DynamoDb is usable. Configure your service to depend on this service
 * if it needs DynamoDb.
 */
interface DynamoDbService : Service
