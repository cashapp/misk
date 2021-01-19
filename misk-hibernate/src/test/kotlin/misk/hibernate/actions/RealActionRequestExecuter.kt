package misk.hibernate.actions

import com.squareup.moshi.Moshi
import misk.client.HttpClientEndpointConfig
import misk.client.HttpClientFactory
import misk.exceptions.BadRequestException
import misk.exceptions.UnauthorizedException
import misk.security.authz.FakeCallerAuthenticator
import misk.web.jetty.JettyService
import misk.web.mediatype.MediaTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import kotlin.reflect.KClass

/**
 * Provide a way to send real requests to the actions to be tested
 * This allows testing of authentication gated functionality
 */
internal class RealActionRequestExecuter<RQ : Any, RS : Any> @Inject constructor(
  private val moshi: Moshi,
  private val jetty: JettyService,
  private val httpClientFactory: HttpClientFactory,
) {
  lateinit var requestPath: String

  /** Set the request path for the Action you want to test to save passing it in with every call */
  fun requestPath(path: String) {
    requestPath = path
  }

  fun executeRequest(
    requestClass: KClass<RQ>,
    responseClass: KClass<RS>,
    request: RQ,
    path: String = requestPath,
    service: String? = null,
    user: String? = null,
    capabilities: String? = null
  ): RS {
    val client = createOkHttpClient()

    val requestAdapter = moshi.adapter(requestClass.java)

    val baseUrl = jetty.httpServerUrl
    val requestBuilder = Request.Builder()
      .post(requestAdapter.toJson(request).toRequestBody(MediaTypes.APPLICATION_JSON.toMediaType()))
      .url(baseUrl.resolve(path)!!)
    service?.let {
      requestBuilder.header(FakeCallerAuthenticator.SERVICE_HEADER, service)
    }
    user?.let {
      requestBuilder.header(FakeCallerAuthenticator.USER_HEADER, user)
    }
    capabilities?.let {
      requestBuilder.header(FakeCallerAuthenticator.CAPABILITIES_HEADER, capabilities)
    }
    val call = client.newCall(requestBuilder.build())
    val response = call.execute()
    val responseAdaptor = moshi.adapter(responseClass.java)
    return when {
      response.isSuccessful -> {
        responseAdaptor.fromJson(response.body!!.source())!!
      }
      response.code == 400 -> {
        throw BadRequestException(response.message)
      }
      response.code == 403 -> {
       throw UnauthorizedException(response.message)
      }
      // TODO add other status code => Misk Action Exception mappings for related tests
      else -> {
        throw IllegalStateException(response.toString())
      }
    }
  }

  private fun createOkHttpClient(): OkHttpClient {
    val config = HttpClientEndpointConfig(jetty.httpServerUrl.toString())
    return httpClientFactory.create(config)
  }
}

internal inline fun <reified RQ : Any, reified RS : Any> RealActionRequestExecuter<RQ, RS>.executeRequest(
  request: RQ,
  service: String? = null,
  user: String? = null,
  capabilities: String? = null
): RS = executeRequest(
  RQ::class,
  RS::class,
  request,
  service = service,
  user = user,
  capabilities = capabilities
)