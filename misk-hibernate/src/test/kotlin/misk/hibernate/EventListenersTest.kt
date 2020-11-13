package misk.hibernate

import com.google.inject.util.Modules
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.event.spi.EventType
import org.junit.jupiter.api.Test
import java.time.LocalDate
import javax.inject.Inject

@MiskTest(startService = true)
class EventListenersTest {
  @MiskTestModule
  val module = Modules.combine(
      MoviesTestModule(),
      object : HibernateEntityModule(Movies::class) {
        override fun configureHibernate() {
          bindListener(EventType.PRE_LOAD).to<FakeEventListener>()
          bindListener(EventType.PRE_INSERT).to<FakeEventListener>()
          bindListener(EventType.PRE_UPDATE).to<FakeEventListener>()
          bindListener(EventType.PRE_DELETE).to<FakeEventListener>()
        }
      })

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var eventListener: FakeEventListener

  @Test
  fun happyPath() {
    transacter.transaction { session ->
      val movie = DbMovie("Star Wars", LocalDate.of(1977, 5, 25))
      session.save(movie)
      assertThat(eventListener.takeEvents()).containsExactly("preinsert")

      movie.name = "A New Hope"
      session.hibernateSession.update(movie) // TODO(jwilson): expose session.update() directly.
      assertThat(eventListener.takeEvents()).isEmpty()
      session.hibernateSession.flush()
      assertThat(eventListener.takeEvents()).containsExactly("preupdate")
    }

    transacter.transaction { session ->
      val movie = queryFactory.newQuery<MovieQuery>().allowFullScatter().allowTableScan()
          .uniqueResult(session)!!
      assertThat(eventListener.takeEvents()).containsExactly("preload")

      session.hibernateSession.delete(movie) // TODO(jwilson): expose session.delete() directly.
      assertThat(eventListener.takeEvents()).isEmpty()
      session.hibernateSession.flush()
      assertThat(eventListener.takeEvents()).containsExactly("predelete")
    }
  }
}
