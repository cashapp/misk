package misk.templating

import com.hubspot.jinjava.Jinjava
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateRenderer @Inject constructor(
    private val jinjava: Jinjava
) {
    fun render(sourcePath: String, context: Map<String, Any> = mapOf()): String {
        val resource = jinjava.resourceLocator.getString(sourcePath, Charset.defaultCharset(), null)
        return jinjava.render(resource, context)
    }
}
