package misk.hibernate

import com.google.common.util.concurrent.UncheckedExecutionException
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.inject.Inject
import kotlin.test.assertFailsWith

@MiskTest
class ReflectionQueryFactoryValidationTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory

  @Test
  fun returnTypeMustBeThis() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
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
    assertThat(assertFailsWith<UncheckedExecutionException> {
      queryFactory.newQuery<ParameterCountMustMatchOperator>()
    }.cause).hasMessage("""
        |Query class ${ParameterCountMustMatchOperator::class.java.name} has problems:
        |  name() declares 0 parameters but must accept 1 parameters""".trimMargin())
  }

  interface ParameterCountMustMatchOperator : Query<DbCharacter> {
    @Constraint("name")
    fun name(): ParameterCountMustMatchOperator
  }

  @Test
  fun annotationRequiredOnQuery() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      queryFactory.newQuery<AnnotationRequiredOnQuery>()
    }.cause).hasMessage("""
        |Query class ${AnnotationRequiredOnQuery::class.java.name} has problems:
        |  name() must be annotated @Constraint, @Order or @Select""".trimMargin())
  }

  interface AnnotationRequiredOnQuery : Query<DbCharacter> {
    fun name(name: String): AnnotationRequiredOnQuery
  }

  @Test
  fun malformedPathOnQuery() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      queryFactory.newQuery<MalformedPathOnQuery>()
    }.cause).hasMessage("""
        |Query class ${MalformedPathOnQuery::class.java.name} has problems:
        |  name() path is not valid: '.name'""".trimMargin())
  }

  interface MalformedPathOnQuery : Query<DbCharacter> {
    @Constraint(".name")
    fun name(name: String): AnnotationRequiredOnQuery
  }

  @Test
  fun parameterAnnotationRequiredOnProjection() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<ParameterAnnotationRequiredOnProjectionQuery>()
      }
    }.cause).hasMessage("""
        |Query class ${ParameterAnnotationRequiredOnProjectionQuery::class.java.name} has problems:
        |  ${ParameterAnnotationRequiredOnProjection::class.java.name} parameter 0 is missing a @Property annotation""".trimMargin())
  }

  interface ParameterAnnotationRequiredOnProjectionQuery : Query<DbCharacter> {
    @Select
    fun listAsProjection(session: Session): List<ParameterAnnotationRequiredOnProjection>
  }

  data class ParameterAnnotationRequiredOnProjection(var name: String) : Projection

  @Test
  fun missingPrimaryConstructor() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<MissingPrimaryConstructorQuery>()
      }
    }.cause).hasMessage("""
        |Query class ${MissingPrimaryConstructorQuery::class.java.name} has problems:
        |  ${MissingPrimaryConstructor::class.java.name} has no primary constructor""".trimMargin())
  }

  interface MissingPrimaryConstructorQuery : Query<DbCharacter> {
    @Select
    fun listAsProjection(session: Session): List<MissingPrimaryConstructor>
  }

  interface MissingPrimaryConstructor : Projection

  @Test
  fun malformedPathOnParameter() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<MalformedPathOnParameterQuery>()
      }
    }.cause).hasMessage("""
        |Query class ${MalformedPathOnParameterQuery::class.java.name} has problems:
        |  ${MalformedPathOnParameter::class.java.name} parameter 0 path is not valid: '.name'""".trimMargin())
  }

  interface MalformedPathOnParameterQuery : Query<DbCharacter> {
    @Select
    fun listAsProjection(session: Session): List<MalformedPathOnParameter>
  }

  data class MalformedPathOnParameter(
    @Property(path = ".name") var name: String
  ) : Projection

  @Test
  fun inParameterIsNotVarargOrCollection() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      queryFactory.newQuery<InParameterIsNotVarargOrCollection>()
    }.cause).hasMessage("""
        |Query class ${InParameterIsNotVarargOrCollection::class.java.name} has problems:
        |  nameIn() parameter must be a vararg or a collection""".trimMargin())
  }

  interface InParameterIsNotVarargOrCollection : Query<DbCharacter> {
    @Constraint("name", Operator.IN)
    fun nameIn(name: String): InParameterIsNotVarargOrCollection
  }

  @Test
  fun selectDoesNotAcceptSession() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<SelectDoesNotAcceptSessionQuery>()
      }
    }.cause).hasMessage("""
        |Query class ${SelectDoesNotAcceptSessionQuery::class.java.name} has problems:
        |  listAsProjection() must accept a single Session parameter""".trimMargin())
  }

  interface SelectDoesNotAcceptSessionQuery : Query<DbCharacter> {
    @Select
    fun listAsProjection(): List<MalformedPathOnParameter>
  }

  @Test
  fun selectNonProjectionPathIsEmpty() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<SelectNonProjectionPathIsEmpty>()
      }
    }.cause).hasMessage("""
        |Query class ${SelectNonProjectionPathIsEmpty::class.java.name} has problems:
        |  listAsProjection() path is not valid: ''""".trimMargin())
  }

  interface SelectNonProjectionPathIsEmpty : Query<DbCharacter> {
    @Select
    fun listAsProjection(session: Session): List<String>
  }

  @Test
  fun selectUniqueReturnTypeNullability() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<SelectUniqueReturnTypeNullability>()
      }
    }.cause).hasMessage("""
        |Query class ${SelectUniqueReturnTypeNullability::class.java.name} has problems:
        |  listNames() return type must be a non-null List or a nullable value
        |  uniqueName() return type must be a non-null List or a nullable value""".trimMargin())
  }

  interface SelectUniqueReturnTypeNullability : Query<DbCharacter> {
    @Select("name")
    fun uniqueName(session: Session): String

    @Select("name")
    fun listNames(session: Session): List<String>?
  }

  @Test
  fun tooManyAnnotations() {
    assertThat(assertFailsWith<UncheckedExecutionException> {
      transacter.transaction {
        queryFactory.newQuery<TooManyAnnotations>()
      }
    }.cause).hasMessage("""
        |Query class ${TooManyAnnotations::class.java.name} has problems:
        |  selectOrConstraint() has too many annotations""".trimMargin())
  }

  interface TooManyAnnotations : Query<DbCharacter> {
    @Select("name")
    @Constraint("name")
    fun selectOrConstraint(session: Session): TooManyAnnotations
  }

  @Test
  fun objectMethods() {
    val query = queryFactory.newQuery<EmptyQuery>()
    assertThat(query.hashCode()).isEqualTo(query.hashCode())
    assertThat(query.equals("foo")).isFalse()
    assertThat(query.toString()).isNotNull()
  }

  interface EmptyQuery : Query<DbCharacter>
}
