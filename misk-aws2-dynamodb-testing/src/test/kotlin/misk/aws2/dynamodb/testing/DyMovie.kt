package misk.aws2.dynamodb.testing

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.time.LocalDate

@DynamoDbBean
class DyMovie {
  @get:DynamoDbPartitionKey
  var name: String? = null

  @get:DynamoDbSecondarySortKey(indexNames = ["movies.release_date_index"])
  @get:DynamoDbConvertedBy(LocalDateTypeConverter::class)
  @get:DynamoDbSortKey
  var release_date: LocalDate? = null

  @get:DynamoDbSecondaryPartitionKey(indexNames = ["movies.release_date_index"])
  var directed_by: String? = null
}

internal class LocalDateTypeConverter : AttributeConverter<LocalDate> {
  override fun transformFrom(input: LocalDate): AttributeValue {
    return AttributeValue.builder().s(input.toString()).build()
  }

  override fun transformTo(input: AttributeValue): LocalDate {
    return LocalDate.parse(input.s())
  }

  override fun type(): EnhancedType<LocalDate> {
    return EnhancedType.of(LocalDate::class.java)
  }

  override fun attributeValueType(): AttributeValueType {
    return AttributeValueType.S
  }
}
