package misk.web.jetty

import com.squareup.moshi.Moshi
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WsConnections {
    @Inject lateinit var moshi: Moshi
    private val mapping = ConcurrentHashMap<String, MutableSet<Socket>>()

    fun subscribe(
            socket: Socket,
            topic: String
    ) {
        if (mapping[topic] == null) {
            mapping[topic] = mutableSetOf(socket)
        } else {
            mapping[topic]?.add(socket)
        }
    }

    fun unsubscribeAll(socket: Socket) {
        mapping.forEach { it -> it.value.remove(socket) }
    }

    fun <T : Any> sendJson(
            dataClass: T,
            topic: String
    ) {
        val adapter = moshi.adapter<T>(dataClass::class.java)
        val json = adapter.toJson(dataClass)
        mapping[topic]?.forEach { s -> s.session.remote.sendString(json) }
    }
}
