package misk.grpc.reflect

import com.google.inject.Provides
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import com.squareup.wire.reflector.SchemaReflector
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.Schema
import com.squareup.wire.schema.SchemaLoader
import misk.inject.KAbstractModule
import misk.web.WebActionModule
import misk.web.actions.WebAction
import misk.web.actions.WebActionEntry
import okio.ExperimentalFileSystem
import okio.FileSystem
import okio.Path.Companion.toPath
import wisp.logging.getLogger
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.functions
import kotlin.reflect.full.isSubclassOf

/**
 * Implements gRPC reflect for all gRPC actions installed in this Misk application.
 *
 * This relies on `.proto` files being included in the `.jar` file. If they're missing, reflection
 * won't work for them.
 */
class GrpcReflectModule : KAbstractModule() {
  override fun configure() {
    install(WebActionModule.create<ServerReflectionApi>())
  }

  @Provides @Singleton
  fun provideServiceReflector(schema: Schema): SchemaReflector {
    return SchemaReflector(schema)
  }

  /** Interrogate the installed gRPC actions and create a Wire schema from that. */
  @OptIn(ExperimentalFileSystem::class)
  @Provides @Singleton
  fun provideSchema(webActions: List<WebActionEntry>): Schema {
    val fileSystem = FileSystem.RESOURCES
    val schemaLoader = SchemaLoader(fileSystem)
    schemaLoader.initRoots(
      sourcePath = toSourceLocations(fileSystem, webActions).toList(),
      protoPath = listOf(Location.get("."))
    )
    schemaLoader.loadExhaustively = true
    return schemaLoader.loadSchema()
  }

  @OptIn(ExperimentalFileSystem::class)
  private fun toSourceLocations(
    fileSystem: FileSystem,
    webActions: List<WebActionEntry>
  ): Set<Location> {
    val result = mutableSetOf<Location>()
    for (webAction in webActions) {
      val wireRpcAnnotation = getWireRpcAnnotation(webAction.actionClass) ?: continue
      val sourceFile = wireRpcAnnotation.sourceFile
      if (sourceFile == "") continue // Generated before @WireRpc.sourceFile existed.
      result += Location.get(".", sourceFile)
    }
    result.removeIf {
      val fileDoesNotExist = !fileSystem.exists(it.path.toPath())
      if (fileDoesNotExist) {
        logger.info("Omitting ${it.path} from ServerReflectionApi; file is not in artifact")
      }
      fileDoesNotExist
    }
    return result
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
