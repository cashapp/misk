package misk.dynamodb

/**
 * This version field is used for optimistic locking.
 */
interface DyVersionedEntity {

  /**
   * This version field can be used to do optimistic locking on updates.
   * Note that the DynamoDbMapper will need to be in save mode
   * DynamoDBMapperConfig.SaveBehavior.CLOBBER
   *
   * @DynamoDBVersionAttribute(attributeName = "version")
   */
  var version: Long
}
