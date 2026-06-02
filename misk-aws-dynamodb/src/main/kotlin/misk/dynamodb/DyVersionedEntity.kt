package misk.dynamodb

/** This version field is used for optimistic locking. */
@Deprecated(
  message =
    "AWS SDK v1 DynamoDB is deprecated. Migrate to misk-aws2-dynamodb and use the AWS SDK v2 " +
      "annotation @DynamoDbVersionAttribute directly on your entity classes instead."
)
interface DyVersionedEntity {

  /**
   * This version field can be used to do optimistic locking on updates. Note that the DynamoDbMapper will need to be in
   * save mode DynamoDBMapperConfig.SaveBehavior.CLOBBER
   *
   * @DynamoDBVersionAttribute(attributeName = "version")
   */
  var version: Long
}
