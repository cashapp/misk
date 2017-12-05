package misk.web.interceptors

import com.google.common.collect.Lists
import com.google.common.truth.Truth.assertThat
import helpers.protos.Dinosaur
import misk.Interceptor
import misk.asAction
import misk.testing.MiskTestRule
import misk.web.Get
import misk.web.ProtobufResponseBody
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.actions.asChain
import misk.web.interceptors.ProtobufInterceptorFactory.ProtobufInterceptor
import okio.Okio
import okio.Pipe
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject
import kotlin.reflect.KFunction

class ProtobufInterceptorFactoryTest {
  @get:Rule
  val miskTestRule = MiskTestRule()

  @Inject internal lateinit var protobufAction: ProtobufAction
  @Inject internal lateinit var protobufInterceptorFactory: ProtobufInterceptorFactory<*, *>
  @Inject internal lateinit var boxResponseInterceptorFactory: BoxResponseInterceptorFactory

  @Test
  fun test() {
    val action = protobufAction::hello.asAction()

    val boxResponseInterceptor: Interceptor = boxResponseInterceptorFactory.create(action)!!
    val protobufInterceptor: ProtobufInterceptor<*> = protobufInterceptorFactory.create(action)!!

    val chain = protobufAction.asChain(
        ProtobufAction::class.members.first() as KFunction<*>,
        Lists.newArrayList("T-Rex"),
        protobufInterceptor, boxResponseInterceptor
    )

    @Suppress("UNCHECKED_CAST")
    val result = chain.proceed(chain.args) as Response<ResponseBody>

    val pipe = Pipe(1024)
    val bufferedSink = Okio.buffer(pipe.sink())
    result.body.writeTo(bufferedSink)
    bufferedSink.close()
    val dinosaur = Dinosaur.ADAPTER.decode(Okio.buffer(pipe.source()))

    assertThat(dinosaur.name).isEqualTo("T-Rex")
  }
}

internal class ProtobufAction : WebAction {
  @Get("/hello/{dinosaur}")
  @ProtobufResponseBody
  fun hello(
    dinosaur: String
  ): Dinosaur {
    return Dinosaur.Builder()
        .name(dinosaur)
        .build()
  }
}
