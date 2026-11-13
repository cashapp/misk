package misk.audit

import com.google.inject.Key
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.Collections
import java.util.Enumeration
import misk.Action
import misk.audit.AuditRequestCaptures.Companion.CORRELATION_HEADER
import misk.inject.keyOf
import misk.scope.SeedDataTransformer
import misk.web.PathPattern
import misk.web.WebActionSeedDataTransformerFactory

@Singleton
internal class AuditCaptureHeaderTransformer @Inject constructor() :
  WebActionSeedDataTransformerFactory, SeedDataTransformer {
  override fun create(pathPattern: PathPattern, action: Action): SeedDataTransformer = this

  override fun transform(seedData: Map<Key<*>, Any?>): Map<Key<*>, Any?> {
    val servletRequest = seedData[keyOf<HttpServletRequest>()] as? HttpServletRequest ?: return seedData
    return seedData + (keyOf<HttpServletRequest>() to CaptureHeaderRequest(servletRequest))
  }

  private class CaptureHeaderRequest(delegate: HttpServletRequest) : HttpServletRequestWrapper(delegate) {
    override fun getHeader(name: String): String? =
      if (name.equals(CORRELATION_HEADER, ignoreCase = true)) null else super.getHeader(name)

    override fun getHeaders(name: String): Enumeration<String> =
      if (name.equals(CORRELATION_HEADER, ignoreCase = true)) Collections.emptyEnumeration() else super.getHeaders(name)

    override fun getHeaderNames(): Enumeration<String> =
      Collections.enumeration(
        super.getHeaderNames().toList().filterNot { it.equals(CORRELATION_HEADER, ignoreCase = true) }
      )

    override fun getIntHeader(name: String): Int =
      if (name.equals(CORRELATION_HEADER, ignoreCase = true)) -1 else super.getIntHeader(name)

    override fun getDateHeader(name: String): Long =
      if (name.equals(CORRELATION_HEADER, ignoreCase = true)) -1L else super.getDateHeader(name)
  }
}
