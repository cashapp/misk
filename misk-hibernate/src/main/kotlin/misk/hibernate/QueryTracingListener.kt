package misk.hibernate

import io.opentracing.Scope
import io.opentracing.Tracer
import misk.hibernate.QueryTracingSpanNames.Companion.DB_DELETE
import misk.hibernate.QueryTracingSpanNames.Companion.DB_INSERT
import misk.hibernate.QueryTracingSpanNames.Companion.DB_UPDATE
import misk.logging.getLogger
import org.hibernate.event.spi.PostDeleteEvent
import org.hibernate.event.spi.PostDeleteEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.PostUpdateEvent
import org.hibernate.event.spi.PostUpdateEventListener
import org.hibernate.event.spi.PreDeleteEvent
import org.hibernate.event.spi.PreDeleteEventListener
import org.hibernate.event.spi.PreInsertEvent
import org.hibernate.event.spi.PreInsertEventListener
import org.hibernate.event.spi.PreUpdateEvent
import org.hibernate.event.spi.PreUpdateEventListener
import org.hibernate.persister.entity.EntityPersister
import javax.inject.Inject
import javax.inject.Singleton

private val logger = getLogger<QueryTracingListener>()

/**
 * Listener to hook up tracing span for UPDATE/INSERT/DELETE queries.
 *
 * Hibernate does not support any good events that gets triggered only ONCE when SELECT query is
 * being executed. The closest it has is PreLoadEvent/PostLoadEvent which gets triggered for each
 * entity being returned by the SELECT query. The SELECT query tracing can be found under
 * {@see misk.hibernate.ReflectionQuery}.
 */
@Singleton
internal class QueryTracingListener @Inject constructor() :
  PreInsertEventListener,
    PostInsertEventListener,
    PreUpdateEventListener,
    PostUpdateEventListener,
    PreDeleteEventListener,
    PostDeleteEventListener {

  @com.google.inject.Inject(optional = true) var tracer: Tracer? = null

  private val lastScopeInThread: ThreadLocal<Scope?> = ThreadLocal.withInitial { null }

  override fun onPreInsert(event: PreInsertEvent?): Boolean {
    startSpan(DB_INSERT)
    return false
  }

  override fun onPostInsert(event: PostInsertEvent?) {
    endLastSpan()
  }

  override fun onPreUpdate(event: PreUpdateEvent?): Boolean {
    startSpan(DB_UPDATE)
    return false
  }

  override fun onPostUpdate(event: PostUpdateEvent?) {
    endLastSpan()
  }

  override fun onPreDelete(event: PreDeleteEvent?): Boolean {
    startSpan(DB_DELETE)
    return false
  }

  override fun onPostDelete(event: PostDeleteEvent?) {
    endLastSpan()
  }

  override fun requiresPostCommitHanding(persister: EntityPersister?): Boolean {
    return false
  }

  private fun startSpan(spanName: String) {
    if (tracer == null) {
      return
    }

    val lastScope = lastScopeInThread.get()
    lastScope?.let {
      logger.info { "lastScope wasn't closed" }
      endLastSpan()
    }

    val spanBuilder = tracer!!.buildSpan(spanName)
    tracer!!.activeSpan()?.let { spanBuilder.asChildOf(it) }
    val span = spanBuilder.start()
    val scope = tracer!!.activateSpan(span)
    lastScopeInThread.set(scope)
  }

  fun endLastSpan() {
    val activeSpan = tracer?.activeSpan()
    val lastScope = lastScopeInThread.get()
    lastScope?.close()
    activeSpan?.finish()
    lastScopeInThread.set(null)
  }
}
