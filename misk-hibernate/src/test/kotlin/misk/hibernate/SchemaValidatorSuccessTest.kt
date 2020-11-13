package misk.hibernate

import com.google.common.collect.Iterables.getOnlyElement
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class SchemaValidatorSuccessTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject @Movies lateinit var sessionFactoryService: SessionFactoryService
  @Inject @Movies lateinit var service: SchemaValidatorService

  @Test
  @Disabled("https://github.com/cashapp/misk/issues/1171")
  fun happyPath() {
    val report = SchemaValidator().validate(transacter, sessionFactoryService.hibernateMetadata)
    assertThat(report.schemas)
        .containsExactlyInAnyOrder("vt_actors_0", "vt_main_0", "vt_movies_-80", "vt_movies_80-")
    assertThat(report.tables)
        .containsExactlyInAnyOrder("actors", "characters", "movies")
    assertThat(report.columns)
        .contains("actors.id", "actors.birth_date", "characters.id", "movies.id")
    assertThat(report.columns.size).isGreaterThanOrEqualTo(15)
    assertThat(getOnlyElement(service.status().messages))
        .startsWith("SchemaValidatorService: Movies is valid: schemas=[vt_actors_0, vt_main_0")
  }
}
