package misk.hibernate

import com.google.inject.Provider
import jakarta.inject.Inject
import misk.jdbc.DataSourceConfig
import misk.testing.MiskExternalDependency
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import misk.vitess.testing.utilities.DockerVitess
import org.junit.jupiter.api.Test

@MiskTest(startService = true)
class VitessSchemaValidatorTest {
  @MiskExternalDependency private val dockerVitess = DockerVitess()

  @MiskTestModule val module = MoviesTestModule()

  @Inject @Movies private lateinit var sessionFactoryService: Provider<SessionFactoryService>
  @Inject @Movies lateinit var transacter: Transacter
  @Inject @Movies lateinit var config: DataSourceConfig

  /**
   * The bulk of the detailed tests is against single sharded MySQL, this just makes sure it also works on Vitess as
   * most of the code is shared.
   */
  @Test
  fun validSchemaValidates() {
    SchemaValidator().validate(transacter, sessionFactoryService.get().hibernateMetadata)
  }
}
