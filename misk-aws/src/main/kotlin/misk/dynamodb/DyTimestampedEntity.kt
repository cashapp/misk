package misk.dynamodb

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

abstract class DyTimestampedEntity : DyEntity() {

  @DynamoDBAttribute(attributeName = "created_at")
  @DynamoDBTypeConverted(converter = InstantConverter::class)
  var created_at: Instant = Clock.systemUTC().instant()
    private set

  @DynamoDBAttribute(attributeName = "updated_at")
  @DynamoDBTypeConverted(converter = UpdatedAtConverter::class)
  var updated_at: Instant = Clock.systemUTC().instant()
    private set

  class UpdatedAtConverter : DynamoDBTypeConverter<String, Instant> {
    @Inject private lateinit var clock: Clock

    override fun convert(`object`: Instant): String {
      return clock.instant().toString()
    }

    override fun unconvert(`object`: String): Instant {
      return Instant.parse(`object`)
    }
  }
}
