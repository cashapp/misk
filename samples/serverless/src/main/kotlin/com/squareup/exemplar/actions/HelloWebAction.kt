package com.squareup.exemplar.actions

import com.google.inject.Guice
import com.google.inject.spi.Elements
import com.google.inject.Module
import com.squareup.exemplar.actions.HelloWebAction.Companion.ServerlessModule
import misk.web.Get
import misk.web.PathParam
import misk.web.QueryParam
import misk.web.RequestHeaders
import misk.web.ResponseContentType
import misk.web.actions.WebAction
import misk.web.mediatype.MediaTypes
import okhttp3.Headers
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy.RUNTIME
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Singleton
class HelloWebAction @Inject constructor(
  val datastore: HelloDatastore
) : WebAction {
  @Get("/hello/{name}")
  @ResponseContentType(MediaTypes.APPLICATION_JSON)
  fun hello(
    @PathParam name: String,
    @RequestHeaders headers: Headers,
    @QueryParam nickName: String?,
    @QueryParam greetings: List<String>?
  ): HelloResponse {
    println(headers["User-agent"])
    return HelloResponse(
        greetings?.joinToString(separator = " ") ?: "YO",
        nickName?.toUpperCase() ?: name.toUpperCase())
  }

  companion object {
    object ProductionOnlyModule : ServerlessComponent("HelloWebAction") {
      override fun configure() {
        bind(HelloDatastore::class).to(RealHelloDatastore::class)
      }
    }

    object CommonModule : ServerlessComponent("HelloWebAction") {
      override fun configure() {
//        install(WebActionModule.create(HelloWebAction::class))
        install(DynamoDbClientModule(ClientSyncDb::class, "clientsync"))

        // sqm upload-secret sessions_hash_hmac_key -n HelloWebAction {Service}
        install(SecretConsumer(name = "sessions_hash_hmac_key"))

        // platform code needs to be able to go from a client interface to a route
        install(ActionModule(NewswriterClient::class))

        // other actions, mysql, external APIs, internal class bindings

        // other actions -> you have to implement real/fake, framework decides for you

      }
    }
  }
}

// Do you produce this by
//   - looking at annotations during build?
//   - actually running code during CI
//   - pulling manifest from the infra provisioning  -> swap this, infra provisioning is driven by generated manifest
data class ActionManifest(
  val name: String,
  val dynamoDbs: List<String>
)



// app.cash.clientsync.SyncHashSecret

class HelloDatastore(@ClientSyncDb dynamoDb: DynamoDb) {

}

data class HelloResponse(val greeting: String, val name: String)

@Qualifier
@Documented
@Retention(RUNTIME)
annotation class ClientSyncDb

fun main() {
  getDependenciesProvidedByFramework(ServerlessModule)

  val createInjector = Guice.createInjector(ServerlessModule)
  createInjector.getInstance(HelloWebAction::class.java)
}

private fun getDependenciesProvidedByFramework(anyModule: Module) {
  val elements = Elements.getElements(anyModule)

  for (element in elements) {
    println(element)
  }
}









