package misk.web.exceptions

import misk.exceptions.ActionException
import misk.exceptions.NotFoundException
import misk.web.Response
import misk.web.ResponseBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

internal class ExceptionMapperResolverTest {

  val mappers = mutableMapOf<KClass<*>, ExceptionMapper<*>>()
  val resolver = ExceptionMapperResolver(mappers)

  @Test
  fun resolvesToSpecificMapper() {
    mappers[NotFoundException::class] = NotFoundExceptionMapper()
    mappers[ActionException::class] = ActionExceptionMapper()

    assertThat(resolver.mapperFor(NotFoundException()))
      .isInstanceOf(NotFoundExceptionMapper::class.java)
  }

  @Test
  fun resolvesToSuperClassMapper() {
    mappers[ActionException::class] = ActionExceptionMapper()

    assertThat(resolver.mapperFor(NotFoundException()))
      .isInstanceOf(ActionExceptionMapper::class.java)
  }

  @Test
  fun noMapperFound() {
    assertThat(resolver.mapperFor(NotFoundException()))
      .isNull()
  }

  @Test
  fun nonActionException() {
    mappers[ArithmeticException::class] = ArithmeticExceptionMapper()

    assertThat(resolver.mapperFor(ArithmeticException()))
      .isInstanceOf(ArithmeticExceptionMapper::class.java)
  }

  class NotFoundExceptionMapper : BaseExceptionMapper<NotFoundException>()
  class ActionExceptionMapper : BaseExceptionMapper<ActionException>()
  class ArithmeticExceptionMapper : BaseExceptionMapper<ArithmeticException>()

  open class BaseExceptionMapper<in T : Throwable> : ExceptionMapper<T> {
    override fun toResponse(th: T): Response<ResponseBody> {
      TODO("")
    }

    override fun canHandle(th: Throwable): Boolean {
      TODO("")
    }
  }
}
