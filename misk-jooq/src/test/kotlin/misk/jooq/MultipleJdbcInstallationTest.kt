package misk.jooq

import jakarta.inject.Inject
import misk.jooq.config.JooqDBIdentifier
import misk.jooq.config.MultipleJdbcInstallationModule
import misk.jooq.model.Genre
import misk.jooq.testgen.tables.references.MOVIE
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
internal class MultipleJdbcInstallationTest {
  @MiskTestModule private var module = MultipleJdbcInstallationModule()
  @Inject @JooqDBIdentifier private lateinit var transacter: JooqTransacter

  @Test fun `does not fail to install both jooq and hibernate modules`() {
    // execute a dummy operation
    transacter.transaction { session ->
      session.ctx.newRecord(MOVIE).apply {
        this.genre = Genre.COMEDY.name
        this.name = "Dumb and dumber"
      }.also { it.store() }
    }
  }
}
