package misk.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.common.collect.Lists
import misk.environment.Environment
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import javax.inject.Inject
import javax.inject.Provider

internal class ConfigProvider<T : Config>(
    private val configClass: Class<out Config>,
    private val appName: String
) : Provider<T> {
    @Inject lateinit var environment: Environment

    override fun get(): T {
        val mapper = ObjectMapper(YAMLFactory())
        mapper.registerModule(KotlinModule())

        var jsonNode: JsonNode? = null
        for (url in getConfigFileUrls()) {
            open(url).use {
                val objectReader = if (jsonNode == null) {
                    mapper.readerFor(JsonNode::class.java)
                } else {
                    mapper.readerForUpdating(jsonNode)
                }
                jsonNode = objectReader.readValue(it)
            }
        }
        if (jsonNode == null) {
            throw IllegalStateException("Could not read JSON from config files")
        }

        @Suppress("UNCHECKED_CAST")
        return mapper.readValue(jsonNode.toString(), configClass) as T
    }

    /**
     *  Returns a list of URLs for configuration files in the order they should be read.
     *  That is, later files in the list should override previous file.
     *
     *  The order we load config files in is:
     *      - app_name-common.yaml
     *      - app_name-environment.yaml
     */
    private fun getConfigFileUrls(): List<URL> {
        val postfixes = Lists.newArrayList("common", environment.name.toLowerCase())

        return postfixes.mapNotNull {
            configClass.classLoader.getResource("$appName-$it.yaml")
        }
    }

    private fun open(url: URL): BufferedReader {
        return BufferedReader(InputStreamReader(url.openStream()))
    }
}

