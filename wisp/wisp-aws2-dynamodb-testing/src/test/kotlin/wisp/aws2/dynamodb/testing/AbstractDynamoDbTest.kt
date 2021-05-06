package wisp.aws2.dynamodb.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.enhanced.dynamodb.Key
import software.amazon.awssdk.enhanced.dynamodb.TableSchema
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import java.time.LocalDate

abstract class AbstractDynamoDbTest {

  lateinit var dynamoDbClient: DynamoDbClient

  lateinit var tables: Set<DynamoDbTable>

  @Test
  fun happyPath() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
    val movieTable = enhancedClient.table("movies", MOVIE_TABLE_SCHEMA)
    val characterTable = enhancedClient.table("characters", CHARACTER_TABLE_SCHEMA)

    val movie = DyMovie()
    movie.name = "Jurassic Park"
    movie.release_date = LocalDate.of(1993, 6, 9)
    movieTable.putItem(movie)

    val character = DyCharacter()
    character.movie_name = "Jurassic Park"
    character.character_name = "Ian Malcolm"
    characterTable.putItem(character)

    // Query the movies created.
    val actualMovie = movieTable.getItem(
      Key.builder()
        .partitionValue("Jurassic Park")
        .sortValue(LocalDate.of(1993, 6, 9).toString())
        .build()
    )
    assertThat(actualMovie.name).isEqualTo(movie.name)
    assertThat(actualMovie.release_date).isEqualTo(movie.release_date)

    val actualCharacter = characterTable.getItem(
      Key.builder()
        .partitionValue("Jurassic Park")
        .sortValue("Ian Malcolm")
        .build()
    )
    assertThat(actualCharacter.movie_name).isEqualTo(character.movie_name)
    assertThat(actualCharacter.character_name).isEqualTo(character.character_name)
  }

  @Test
  fun truncateTables() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
    val movieTable = enhancedClient.table("movies", MOVIE_TABLE_SCHEMA)
    val characterTable = enhancedClient.table("characters", CHARACTER_TABLE_SCHEMA)

    val movie = DyMovie()
    movie.name = "Jurassic Park"
    movie.release_date = LocalDate.of(1993, 6, 9)
    movieTable.putItem(movie)

    val character = DyCharacter()
    character.movie_name = "Jurassic Park"
    character.character_name = "Ian Malcolm"
    characterTable.putItem(character)

    val service = CreateTablesService(dynamoDbClient, tables)
    service.startUp()
    val actualMovie = movieTable.getItem(
      Key.builder()
        .partitionValue("Jurassic Park")
        .sortValue(LocalDate.of(1993, 6, 9).toString())
        .build()
    )
    assertThat(actualMovie).isNull()
    val actualCharacter = characterTable.getItem(
      Key.builder()
        .partitionValue("Jurassic Park")
        .sortValue("Ian Malcolm")
        .build()
    )
    assertThat(actualCharacter).isNull()
  }

  @Test
  fun globalSecondaryIndex() {
    val enhancedClient = DynamoDbEnhancedClient.builder()
      .dynamoDbClient(dynamoDbClient)
      .build()
    val movieTable = enhancedClient.table("movies", MOVIE_TABLE_SCHEMA)

    val movie = DyMovie().apply {
      name = "Jurassic Park"
      release_date = LocalDate.of(1993, 6, 9)
      directed_by = "Steven Spielberg"
    }
    movieTable.putItem(movie)
    val movie2 = DyMovie().apply {
      name = "The Terminal"
      release_date = LocalDate.of(2004, 6, 18)
      directed_by = "Steven Spielberg"
    }
    movieTable.putItem(movie2)
    val movie3 = DyMovie().apply {
      name = "Bridge of Spies"
      release_date = LocalDate.of(2015, 10, 16)
      directed_by = "Steven Spielberg"
    }
    movieTable.putItem(movie3)
    val movie4 = DyMovie().apply {
      name = "Ready Player One"
      release_date = LocalDate.of(2018, 3, 29)
      directed_by = "Steven Spielberg"
    }
    movieTable.putItem(movie4)

    // Query the movies created.
    val query = QueryEnhancedRequest.builder()
      .queryConditional(
        QueryConditional.sortGreaterThanOrEqualTo {
          it.partitionValue("Steven Spielberg")
            .sortValue(LocalDate.of(2010, 1, 1).toString())
        }
      )
      // Consistent read cannot be true when querying a GSI.
      .consistentRead(false)
      .build()

    val newSpielbergMovies = movieTable.index("movies.release_date_index").query(query)
    val newSpielbergMovieNames =
      newSpielbergMovies.stream().flatMap { it.items().stream() }.map { it.name }
    assertThat(newSpielbergMovieNames).contains("Bridge of Spies", "Ready Player One")
  }

  companion object {
    val MOVIE_TABLE_SCHEMA: TableSchema<DyMovie> = TableSchema.fromClass(DyMovie::class.java)
    val CHARACTER_TABLE_SCHEMA: TableSchema<DyCharacter> =
      TableSchema.fromClass(DyCharacter::class.java)
  }
}
