package misk.web.interceptors

import org.assertj.core.api.Assertions.assertThat
import helpers.protos.Dinosaur
import misk.asAction
import misk.testing.ActionTest
import misk.web.Get
import misk.web.ProtobufResponseBody
import misk.web.Response
import misk.web.ResponseBody
import misk.web.actions.WebAction
import misk.web.actions.asChain
import okio.Okio
import okio.Pipe
import org.junit.jupiter.api.Test
import javax.inject.Inject

@ActionTest
class ProtobufInterceptorFactoryTest {
    @Inject internal lateinit var protobufAction: ProtobufAction
    @Inject internal lateinit var protobufInterceptorFactory: ProtobufInterceptorFactory<*, *>
    @Inject internal lateinit var boxResponseInterceptorFactory: BoxResponseInterceptorFactory

    @Test
    fun test() {
        val action = ProtobufAction::hello.asAction()

        val boxResponseInterceptor = boxResponseInterceptorFactory.create(action)!!
        val protobufInterceptor = protobufInterceptorFactory.create(action)!!
        val chain = protobufAction.asChain(ProtobufAction::hello, listOf("T-Rex"),
                protobufInterceptor, boxResponseInterceptor)

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
