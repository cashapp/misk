package misk.client

import java.net.Proxy
import java.net.ProxySelector
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.CertificatePinner
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * A helper stub implementationt that implements okhttp's Interceptor.Chain interface, optionally delegating to a misk
 * ClientNetworkChain if one is provided. If no delegate ClientNetworkChain is provided (i.e. null is passed in for that
 * constructor argument), the caller is responsible for ensuring the proceed()/request() methods are either not called,
 * or overridden.
 */
open class OkHttpInterceptorChain(private val chain: ClientNetworkChain?) : Interceptor.Chain {
  override fun proceed(request: Request): Response {
    return chain!!.proceed(request)
  }

  override fun request(): Request {
    return chain!!.request
  }

  override fun connection(): Connection? {
    return null
  }

  override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    error("not implemented")
  }

  override fun connectTimeoutMillis(): Int {
    error("not implemented")
  }

  override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    error("not implemented")
  }

  override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
    error("not implemented")
  }

  override fun writeTimeoutMillis(): Int {
    error("not implemented")
  }

  override fun call(): Call {
    error("not implemented")
  }

  override fun readTimeoutMillis(): Int {
    error("not implemented")
  }

  override val followSslRedirects: Boolean
    get() = error("not implemented")

  override val followRedirects: Boolean
    get() = error("not implemented")

  override val dns: Dns
    get() = error("not implemented")

  override val socketFactory: SocketFactory
    get() = error("not implemented")

  override val retryOnConnectionFailure: Boolean
    get() = error("not implemented")

  override val authenticator: Authenticator
    get() = error("not implemented")

  override val cookieJar: CookieJar
    get() = error("not implemented")

  override val cache: Cache?
    get() = error("not implemented")

  override val proxy: Proxy?
    get() = error("not implemented")

  override val proxySelector: ProxySelector
    get() = error("not implemented")

  override val proxyAuthenticator: Authenticator
    get() = error("not implemented")

  override val sslSocketFactoryOrNull: SSLSocketFactory?
    get() = error("not implemented")

  override val x509TrustManagerOrNull: X509TrustManager?
    get() = error("not implemented")

  override val hostnameVerifier: HostnameVerifier
    get() = error("not implemented")

  override val certificatePinner: CertificatePinner
    get() = error("not implemented")

  override val connectionPool: ConnectionPool
    get() = error("not implemented")

  override val eventListener: EventListener
    get() = error("not implemented")

  override fun withDns(dns: Dns): Interceptor.Chain {
    error("not implemented")
  }

  override fun withSocketFactory(socketFactory: SocketFactory): Interceptor.Chain {
    error("not implemented")
  }

  override fun withRetryOnConnectionFailure(retryOnConnectionFailure: Boolean): Interceptor.Chain {
    error("not implemented")
  }

  override fun withAuthenticator(authenticator: Authenticator): Interceptor.Chain {
    error("not implemented")
  }

  override fun withCookieJar(cookieJar: CookieJar): Interceptor.Chain {
    error("not implemented")
  }

  override fun withCache(cache: Cache?): Interceptor.Chain {
    error("not implemented")
  }

  override fun withProxy(proxy: Proxy?): Interceptor.Chain {
    error("not implemented")
  }

  override fun withProxySelector(proxySelector: ProxySelector): Interceptor.Chain {
    error("not implemented")
  }

  override fun withProxyAuthenticator(proxyAuthenticator: Authenticator): Interceptor.Chain {
    error("not implemented")
  }

  override fun withSslSocketFactory(
    sslSocketFactory: SSLSocketFactory?,
    x509TrustManager: X509TrustManager?,
  ): Interceptor.Chain {
    error("not implemented")
  }

  override fun withHostnameVerifier(hostnameVerifier: HostnameVerifier): Interceptor.Chain {
    error("not implemented")
  }

  override fun withCertificatePinner(certificatePinner: CertificatePinner): Interceptor.Chain {
    error("not implemented")
  }

  override fun withConnectionPool(connectionPool: ConnectionPool): Interceptor.Chain {
    error("not implemented")
  }
}
