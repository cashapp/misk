package misk.hibernate

import com.google.common.util.concurrent.UncheckedExecutionException
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
class ReflectionQueryFactoryValidationTest {
  @MiskTestModule
  val module = HibernateTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun returnTypeMustBeThis() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      queryFactory.newQuery<ReturnTypeMustBeThis>()
    }.cause).hasMessage("""
        |Query class ${ReturnTypeMustBeThis::class.java.name} has problems:
        |  name() returns ${String::class.java.name} but @Constraint methods must return this (${ReturnTypeMustBeThis::class.java.name})""".trimMargin())
  }

  interface ReturnTypeMustBeThis : Query<DbCharacter> {
    @Constraint("name")
    fun name(name: String): String
  }

  @Test
  fun parameterCountMustMatchOperator() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      queryFactory.newQuery<ParameterCountMustMatchOperator>()
    }.cause).hasMessage("""
        |Query class ${ParameterCountMustMatchOperator::class.java.name} has problems:
        |  name() declares 0 parameters but must accept 1 parameter""".trimMargin())
  }

  interface ParameterCountMustMatchOperator : Query<DbCharacter> {
    @Constraint("name")
    fun name(): ParameterCountMustMatchOperator
  }

  @Test
  fun constraintAnnotationRequiredOnQuery() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      queryFactory.newQuery<ConstraintAnnotationRequiredOnQuery>()
    }.cause).hasMessage("""
        |Query class ${ConstraintAnnotationRequiredOnQuery::class.java.name} has problems:
        |  name() is missing a @Constraint annotation""".trimMargin())
  }

  interface ConstraintAnnotationRequiredOnQuery : Query<DbCharacter> {
    fun name(name: String): ConstraintAnnotationRequiredOnQuery
  }

  @Test
  fun malformedPathOnQuery() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      queryFactory.newQuery<MalformedPathOnQuery>()
    }.cause).hasMessage("""
        |Query class ${MalformedPathOnQuery::class.java.name} has problems:
        |  name() path is not valid: '.name'""".trimMargin())
  }

  interface MalformedPathOnQuery : Query<DbCharacter> {
    @Constraint(".name")
    fun name(name: String): ConstraintAnnotationRequiredOnQuery
  }

  @Test
  fun operatorMustBeKnown() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      queryFactory.newQuery<OperatorMustBeKnown>()
    }.cause).hasMessage("""
        |Query class ${OperatorMustBeKnown::class.java.name} has problems:
        |  name() has an unknown operator: '~'""".trimMargin())
  }

  interface OperatorMustBeKnown : Query<DbCharacter> {
    @Constraint(path = "name", operator = "~")
    fun name(name: String): OperatorMustBeKnown
  }

  @Test
  fun parameterAnnotationRequiredOnProjection() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      transacter.transaction { session ->
        queryFactory.newQuery<EmptyQuery>().listAs<ParameterAnnotationRequiredOnProjection>(session)
      }
    }.cause).hasMessage("""
        |Projection class ${ParameterAnnotationRequiredOnProjection::class.java.name} has problems:
        |  parameter 0 is missing a @Property annotation""".trimMargin())
  }

  data class ParameterAnnotationRequiredOnProjection(var name: String) : Projection

  @Test
  fun missingPrimaryConstructor() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      transacter.transaction { session ->
        queryFactory.newQuery<EmptyQuery>().listAs<MissingPrimaryConstructor>(session)
      }
    }.cause).hasMessage("""
        |Projection class ${MissingPrimaryConstructor::class.java.name} has problems:
        |  this type has no primary constructor""".trimMargin())
  }

  interface MissingPrimaryConstructor : Projection

  @Test
  fun malformedPathOnParameter() {
    assertThat(assertThrows(UncheckedExecutionException::class.java) {
      transacter.transaction { session ->
        queryFactory.newQuery<EmptyQuery>().listAs<MalformedPathOnParameter>(session)
      }
    }.cause).hasMessage("""
        |Projection class ${MalformedPathOnParameter::class.java.name} has problems:
        |  parameter 0 path is not valid: '.name'""".trimMargin())
  }

  data class MalformedPathOnParameter(
    @Property(".name") var name: String
  ) : Projection

  @Test
  fun objectMethods() {
    val query = queryFactory.newQuery<EmptyQuery>()
    assertThat(query.hashCode()).isEqualTo(query.hashCode())
    assertThat(query.equals("foo")).isFalse()
    assertThat(query.toString()).isNotNull()
  }

  interface EmptyQuery : Query<DbCharacter> {
  }
}
