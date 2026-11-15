package misk.grpc.reflect

import com.google.inject.Provides
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import com.squareup.wire.reflector.SchemaReflector
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.PruningRules
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import jakarta.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf
import misk.inject.KAbstractModule
import misk.logging.getLogger
import misk.web.WebActionModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * Implements gRPC reflect for all gRPC actions installed in this Misk application.
 *
 * This relies on `.proto` files being included in the `.jar` file. If they're missing, reflection won't work for them.
 */
class GrpcReflectModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ServerReflectionApi>())
  }

  @Provides
  @Singleton
  fun provideServiceReflector(schema: Schema): SchemaReflector {
    return SchemaReflector(schema)
  }

  /** Interrogate the installed gRPC actions and create a Wire schema from that. */
  @Provides
  @Singleton
  fun provideSchema(webActions: List<WebActionEntry>): Schema {
    val fileSystem = FileSystem.RESOURCES
    val sourceLocations = mutableSetOf<Location>()
    val implementedServices = mutableSetOf<String>()

    for (webAction in webActions) {
      val wireRpc = getWireRpcAnnotation(webAction.actionClass) ?: continue
      val sourceFile = wireRpc.sourceFile
      if (sourceFile == "") continue

      val location = Location.get(".", sourceFile)
      if (!fileSystem.exists(location.path.toPath())) {
        logger.info("Omitting ${location.path} from ServerReflectionApi; file is not in artifact")
        continue
      }

      sourceLocations += location
      serviceNameFromPath(wireRpc.path)?.let { implementedServices += it }
    }

    val schemaLoader = SchemaLoader(fileSystem)
    schemaLoader.initRoots(
      sourcePath = sourceLocations.toList(),
      protoPath = listOf(Location.get(".")),
    )
    schemaLoader.loadExhaustively = true
    val schema = schemaLoader.loadSchema()

    val pruningRules = PruningRules.Builder()
      .addRoot(implementedServices)
      .build()
    return schema.prune(pruningRules)
  }

  private fun serviceNameFromPath(path: String): String? {
    if (!path.startsWith("/")) return null
    val secondSlash = path.indexOf('/', startIndex = 1)
    if (secondSlash == -1) return null
    val serviceName = path.substring(1, secondSlash)
    return serviceName.takeIf { it.isNotEmpty() }
  }

  /** Find `@WireRpc` on a function of one of the supertypes and return it. */
  private fun getWireRpcAnnotation(actionClass: KClass<out WebAction>): WireRpc? {
    for (supertype in actionClass.supertypes) {
      val kClass = supertype.classifier as? KClass<*> ?: continue
      if (!kClass.isSubclassOf(Service::class)) continue
      for (function in kClass.functions) {
        val wireRpc = function.findAnnotation<WireRpc>()
        if (wireRpc != null) return wireRpc
      }
    }
    return null
  }

  companion object {
    private val logger = getLogger<GrpcReflectModule>()
  }
}
