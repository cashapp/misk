package misk.hibernate

import com.google.common.util.concurrent.AbstractIdleService
import com.google.common.util.concurrent.Service
import misk.healthchecks.HealthCheck
import misk.healthchecks.HealthStatus
import java.util.Collections
import javax.inject.Provider
import kotlin.reflect.KClass

internal class SchemaValidatorService internal constructor(
  private val qualifier: KClass<out Annotation>,
  private val sessionFactoryServiceProvider: Provider<SessionFactoryService>,
  private val transacterProvider: Provider<Transacter>
) : AbstractIdleService(), HealthCheck {
  private lateinit var report: ValidationReport

  override fun startUp() {
    report = reports.computeIfAbsent(qualifier) {
      val validator = SchemaValidator()
      val sessionFactoryService = sessionFactoryServiceProvider.get()
      validator.validate(transacterProvider.get(), sessionFactoryService.hibernateMetadata)
    }
  }

  override fun shutDown() {
  }

  override fun status(): HealthStatus {
    val state = state()
    if (state != Service.State.RUNNING) {
      return HealthStatus.unhealthy("SchemaValidatorService: ${qualifier.simpleName} is $state")
    }

    return HealthStatus.healthy(
      "SchemaValidatorService: ${qualifier.simpleName} is valid: " +
        "schemas=${report.schemas} tables=${report.tables} columnCount=${report.columns.size} " +
        "columns=${report.columns}"
    )
  }

  companion object {
    /** Make sure we only validate each database once. It can be quite slow sometimes. */
    private val reports: MutableMap<KClass<out Annotation>, ValidationReport> =
      Collections.synchronizedMap(mutableMapOf<KClass<out Annotation>, ValidationReport>())
  }
}
