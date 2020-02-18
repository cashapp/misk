package misk.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import java.time.Instant

class InstantConverter : DynamoDBTypeConverter<String, Instant> {
  override fun convert(`object`: Instant): String {
    return `object`.toString()
  }

  override fun unconvert(`object`: String): Instant {
    return Instant.parse(`object`)
  }
}
