package misk.client

import okhttp3.OkHttpClient

/**
 * Configures the per-method transport for typed HTTP and Wire gRPC clients, after Misk has installed its interceptors
 * and before call factories are wrapped. Register with Guice multibindings.
 *
 * Use this for transport settings or event listeners that cannot be implemented by an interceptor. Preserve existing
 * listeners when installing an additional listener.
 */
interface ClientOkHttpConfigurator {
  fun configure(action: ClientAction, builder: OkHttpClient.Builder)
}
