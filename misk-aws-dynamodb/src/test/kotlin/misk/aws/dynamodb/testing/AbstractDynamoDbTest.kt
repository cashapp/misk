package misk.aws.dynamodb.testing

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator
import com.amazonaws.services.dynamodbv2.model.Condition
import com.google.common.util.concurrent.ServiceManager
import misk.dynamodb.DynamoDbHealthCheck
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

abstract class AbstractDynamoDbTest {
  @Inject lateinit var dynamoDbClient: AmazonDynamoDB
  @Inject lateinit var healthCheck: DynamoDbHealthCheck
  @Inject lateinit var serviceManager: ServiceManager

  @Test
  fun happyPath() {
    val dynamoDbMapper = DynamoDBMapper(dynamoDbClient)
    val movieMapper = dynamoDbMapper.newTableMapper<DyMovie, String, LocalDate>(DyMovie::class.java)
    val characterMapper =
      dynamoDbMapper.newTableMapper<DyCharacter, String, String>(DyCharacter::class.java)

    val movie = DyMovie()
    movie.name = "Jurassic Park"
    movie.release_date = LocalDate.of(1993, 6, 9)
    movieMapper.save(movie)

    val character = DyCharacter()
    character.movie_name = "Jurassic Park"
    character.character_name = "Ian Malcolm"
    characterMapper.save(character)

    // Query the movies created.
    val actualMovie = movieMapper.load("Jurassic Park", LocalDate.of(1993, 6, 9))
    assertThat(actualMovie.name).isEqualTo(movie.name)
    assertThat(actualMovie.release_date).isEqualTo(movie.release_date)

    val actualCharacter = characterMapper.load("Jurassic Park", "Ian Malcolm")
    assertThat(actualCharacter.movie_name).isEqualTo(character.movie_name)
    assertThat(actualCharacter.character_name).isEqualTo(character.character_name)
  }

  @Test
  fun globalSecondaryIndex() {
    val dynamoDbMapper = DynamoDBMapper(dynamoDbClient)
    val movieMapper = dynamoDbMapper.newTableMapper<DyMovie, String, LocalDate>(DyMovie::class.java)

    val movie = DyMovie().apply {
      name = "Jurassic Park"
      release_date = LocalDate.of(1993, 6, 9)
      directed_by = "Steven Spielberg"
    }
    movieMapper.save(movie)
    val movie2 = DyMovie().apply {
      name = "The Terminal"
      release_date = LocalDate.of(2004, 6, 18)
      directed_by = "Steven Spielberg"
    }
    movieMapper.save(movie2)
    val movie3 = DyMovie().apply {
      name = "Bridge of Spies"
      release_date = LocalDate.of(2015, 10, 16)
      directed_by = "Steven Spielberg"
    }
    movieMapper.save(movie3)
    val movie4 = DyMovie().apply {
      name = "Ready Player One"
      release_date = LocalDate.of(2018, 3, 29)
      directed_by = "Steven Spielberg"
    }
    movieMapper.save(movie4)

    // Query the movies created.
    val query = DynamoDBQueryExpression<DyMovie>()
      .withHashKeyValues(DyMovie().apply { directed_by = "Steven Spielberg" })
      .withRangeKeyCondition(
        "release_date",
        Condition()
          .withComparisonOperator(ComparisonOperator.GE)
          .withAttributeValueList(AttributeValue(LocalDate.of(2010, 1, 1).toString()))
      )
    // Consistent read cannot be true when querying a GSI.
    query.withConsistentRead(false)
    val newSpielbergMovies = movieMapper.query(query)
    val newSpielbergMovieNames = newSpielbergMovies.map { it.name }
    assertThat(newSpielbergMovieNames).contains("Bridge of Spies", "Ready Player One")
  }

  @Test
  fun `healthCheck healthy`() {
    val healthStatus = healthCheck.status()
    assertThat(healthStatus.isHealthy).isTrue()
  }

  @Test
  fun `healthCheck unhealthy`() {
    // Stop the ServiceManager early will disconnect the DynamoDB client.
    serviceManager.stopAsync()
    serviceManager.awaitStopped()
    val healthStatus = healthCheck.status()
    assertThat(healthStatus.isHealthy).isFalse()
  }
}
