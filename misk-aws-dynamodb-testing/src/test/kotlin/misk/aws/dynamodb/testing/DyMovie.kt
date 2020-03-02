package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import java.nio.ByteBuffer
import java.time.LocalDate

@DynamoDBTable(tableName = "movies")
class DyMovie {
  @DynamoDBHashKey(attributeName = "name")
  var name: String = ""
  @DynamoDBTypeConverted(converter = LocalDateTypeConverter::class)
  @DynamoDBRangeKey(attributeName = "release_date")
  var release_date: LocalDate? = LocalDate.now()
}

internal class LocalDateTypeConverter : DynamoDBTypeConverter<String, LocalDate> {
  override fun unconvert(string: String): LocalDate {
    return LocalDate.parse(string)
  }

  override fun convert(localDate: LocalDate): String {
    return localDate.toString()
  }
}
