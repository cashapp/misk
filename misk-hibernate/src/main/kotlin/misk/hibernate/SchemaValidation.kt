package misk.hibernate

import com.google.common.base.CaseFormat
import misk.jdbc.DataSourceConfig
import org.hibernate.SessionFactory
import java.sql.Connection

internal class SchemaValidation(
  private val sessionFactory: SessionFactory,
  private val config: DataSourceConfig
) {

  fun runValidation() {
    val errors = object {
      private val messages = mutableListOf<String>()
      private var errorsFound = false
      fun checkErrors() {
        check(!errorsFound) {
          "Failed schema validation: \n${messages.joinToString(separator = "\n")}"
        }
      }

      fun getMiskSchemaErrors(): MiskSchemaErrors = MiskSchemaValidationError()

      inner class MiskSchemaValidationError(val indent: String = "") : MiskSchemaErrors {
        override fun info(message: String) {
          messages += "$indent${message.replace("\n", "\n$indent")}"
        }

        override fun newChildSchemaErrors(): MiskSchemaErrors = MiskSchemaValidationError(
            "$indent  ")

        override fun validate(expression: Boolean, lambda: () -> String) {
          if (!expression) {
            errorsFound = true
            messages += "$indent- ${lambda().replace("\n", "\n$indent")}."
          }
        }
      }
    }
    val dbMetaData = sessionFactory.doWork { metaData }
    val extractDbSchema: MiskDatabase = DatabaseToMiskSchema(dbMetaData).miskSchema(config.database)

    val metadata = MetadataIntegrator.metadata
    val extractOrmSchema: MiskDatabase = OrmToMiskSchema(metadata).miskSchema()

    DatabaseValidator.process(errors.getMiskSchemaErrors(), extractDbSchema, extractOrmSchema)
    errors.checkErrors()
  }

  private fun <R> SessionFactory.doWork(lambda: Connection.() -> R): R {
    openSession().use { session ->
      return session.doReturningWork { connection ->
        connection.lambda()
      }
    }
  }

  companion object {
    // Attempt to normalize and find potential matches. Make a dumb guess and normalize.
    fun normalize(identifier: String): String =
        if (identifier.contains(Regex("([_\\-])"))) {
          identifier.replace("-", "_").toLowerCase()
        } else {
          // Lets guess the identifier is in CamelCase.
          CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, identifier)
        }
  }
}
