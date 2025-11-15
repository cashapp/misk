package misk.aws2.dynamodb.testing

import com.google.common.util.concurrent.ServiceManager
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.LocalDate

abstract class AbstractQualifiedDynamoDbTest {
  @Inject lateinit var unqualifiedClient: DynamoDbClient
  @Inject @PrimaryDb lateinit var primaryClient: DynamoDbClient
  @Inject @SecondaryDb lateinit var secondaryClient: DynamoDbClient
  @Inject lateinit var serviceManager: ServiceManager

  @Test
  fun unqualifiedModule() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(unqualifiedClient)
      .build()
    val movieTable = enhancedClient.table("movies", MOVIE_TABLE_SCHEMA)

    val movie = DyMovie()
    movie.name = "Unqualified Movie"
    movie.release_date = LocalDate.of(2024, 1, 1)
    movieTable.putItem(movie)

    val actualMovie = movieTable.getItem(
      Key.builder()
        .partitionValue("Unqualified Movie")
        .sortValue(LocalDate.of(2024, 1, 1).toString())
        .build()
    )
    assertThat(actualMovie.name).isEqualTo(movie.name)
    assertThat(actualMovie.release_date).isEqualTo(movie.release_date)
  }

  @Test
  fun primaryQualifiedModule() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(primaryClient)
      .build()
    val characterTable = enhancedClient.table("characters", CHARACTER_TABLE_SCHEMA)

    val character = DyCharacter()
    character.movie_name = "Primary Movie"
    character.character_name = "Primary Character"
    characterTable.putItem(character)

    val actualCharacter = characterTable.getItem(
      Key.builder()
        .partitionValue("Primary Movie")
        .sortValue("Primary Character")
        .build()
    )
    assertThat(actualCharacter.movie_name).isEqualTo(character.movie_name)
    assertThat(actualCharacter.character_name).isEqualTo(character.character_name)
  }

  @Test
  fun secondaryQualifiedModule() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(secondaryClient)
      .build()
    val movieTable = enhancedClient.table("movies", MOVIE_TABLE_SCHEMA)

    val movie = DyMovie()
    movie.name = "Secondary Movie"
    movie.release_date = LocalDate.of(2025, 1, 1)
    movieTable.putItem(movie)

    val actualMovie = movieTable.getItem(
      Key.builder()
        .partitionValue("Secondary Movie")
        .sortValue(LocalDate.of(2025, 1, 1).toString())
        .build()
    )
    assertThat(actualMovie.name).isEqualTo(movie.name)
    assertThat(actualMovie.release_date).isEqualTo(movie.release_date)
  }

  @Test
  fun allQualifiedClientsWork() {
    // Test that unqualified and qualified clients all function concurrently
    val unqualifiedEnhanced = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(unqualifiedClient)
      .build()
    val primaryEnhanced = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(primaryClient)
      .build()
    val secondaryEnhanced = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(secondaryClient)
      .build()

    val unqualifiedMovieTable = unqualifiedEnhanced.table("movies", MOVIE_TABLE_SCHEMA)
    val primaryCharacterTable = primaryEnhanced.table("characters", CHARACTER_TABLE_SCHEMA)
    val secondaryMovieTable = secondaryEnhanced.table("movies", MOVIE_TABLE_SCHEMA)

    // Add items via different qualified clients
    val unqualifiedMovie = DyMovie().apply {
      name = "Unqualified Shared Test"
      release_date = LocalDate.of(2024, 2, 1)
    }
    unqualifiedMovieTable.putItem(unqualifiedMovie)

    val primaryCharacter = DyCharacter().apply {
      movie_name = "Primary Shared Test"
      character_name = "Primary Character"
    }
    primaryCharacterTable.putItem(primaryCharacter)

    val secondaryMovie = DyMovie().apply {
      name = "Secondary Shared Test"
      release_date = LocalDate.of(2025, 2, 1)
    }
    secondaryMovieTable.putItem(secondaryMovie)

    // Verify all clients can access the shared database
    val retrievedUnqualifiedMovie = unqualifiedMovieTable.getItem(
      Key.builder()
        .partitionValue("Unqualified Shared Test")
        .sortValue(LocalDate.of(2024, 2, 1).toString())
        .build()
    )
    assertThat(retrievedUnqualifiedMovie).isNotNull
    assertThat(retrievedUnqualifiedMovie.name).isEqualTo("Unqualified Shared Test")

    val retrievedPrimaryCharacter = primaryCharacterTable.getItem(
      Key.builder()
        .partitionValue("Primary Shared Test")
        .sortValue("Primary Character")
        .build()
    )
    assertThat(retrievedPrimaryCharacter).isNotNull
    assertThat(retrievedPrimaryCharacter.character_name).isEqualTo("Primary Character")

    val retrievedSecondaryMovie = secondaryMovieTable.getItem(
      Key.builder()
        .partitionValue("Secondary Shared Test")
        .sortValue(LocalDate.of(2025, 2, 1).toString())
        .build()
    )
    assertThat(retrievedSecondaryMovie).isNotNull
    assertThat(retrievedSecondaryMovie.name).isEqualTo("Secondary Shared Test")
  }

  companion object {
    val MOVIE_TABLE_SCHEMA: TableSchema<DyMovie> = TableSchema.fromClass(DyMovie::class.java)
    val CHARACTER_TABLE_SCHEMA: TableSchema<DyCharacter> =
      TableSchema.fromClass(DyCharacter::class.java)
  }
}
