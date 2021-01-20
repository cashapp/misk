package misk.hibernate

import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.TypeMismatchException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import javax.inject.Inject

@MiskTest
class ChildEntityTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun loadChildEntityByGid() {

    transacter.transaction { session ->
      val jp = session.save(DbMovie("Jurassic Park", LocalDate.of(1993, 6, 9)))
      val jg = session.save(DbActor("Jeff Goldblum", LocalDate.of(1952, 10, 22)))
      session.save(DbCharacter("Ian Malcolm", session.load(jp), session.load(jg)))
    }

    transacter.transaction { session ->
      val ianMalcolm = queryFactory.newQuery<CharacterQuery>()
          .allowFullScatter().allowTableScan()
          .name("Ian Malcolm")
          .uniqueResult(session)!!
      val ianMalcolmByGid = session.loadSharded(ianMalcolm.gid)
      assertThat(ianMalcolm).isEqualTo(ianMalcolmByGid)
    }
  }

  @Test
  fun exceptionWhenLoadingChildEntityById() {
    val movieId = transacter.transaction { session ->
      session.save(DbMovie("Star Wars", LocalDate.of(1977, 5, 25)))
    }
    val actorId = transacter.transaction { session ->
      session.save(DbActor("Carrie Fisher", null))
    }
    val charId = transacter.transaction { session ->
      session.save(DbCharacter("Leia Organa", session.load(movieId), session.load(actorId)))
    }

    assertThrows<TypeMismatchException> {
      transacter.transaction { session ->
        session.load(charId, DbCharacter::class)
      }
    }
  }
}
