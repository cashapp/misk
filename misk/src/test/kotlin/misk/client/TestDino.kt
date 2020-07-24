package misk.client

import com.google.inject.Provides
import helpers.protos.Dinosaur
import misk.inject.KAbstractModule
import misk.web.Post
import misk.web.ConcurrencyLimitsOptOut
import misk.web.RequestBody
import misk.web.RequestContentType
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import javax.inject.Inject
import javax.inject.Singleton

internal interface ReturnADinosaur {
  @POST("/cooldinos")
  fun getDinosaur(@Body request: Dinosaur): Call<Dinosaur>
}

internal class ReturnADinosaurAction @Inject constructor() : WebAction {
  @Post("/cooldinos")
  @ConcurrencyLimitsOptOut // TODO: Remove after 2020-08-01 (or use @AvailableWhenDegraded).
  @RequestContentType(MediaTypes.APPLICATION_JSON)
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun getDinosaur(@RequestBody request: Dinosaur): Dinosaur = request.newBuilder()
      .name("super${request.name}")
      .build()
}

internal class DinoClientModule(private val jetty: JettyService) : KAbstractModule() {
  override fun configure() {
    install(TypedHttpClientModule.create<ReturnADinosaur>("dinosaur"))
    super.configure()
  }

  @Provides
  @Singleton
  fun provideHttpClientConfig(): HttpClientsConfig {
    return HttpClientsConfig(
        endpoints = mapOf(
            "dinosaur" to HttpClientEndpointConfig(jetty.httpServerUrl.toString())
        ))
  }
}
