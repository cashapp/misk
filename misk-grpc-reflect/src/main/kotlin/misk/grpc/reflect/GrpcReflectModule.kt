package misk.grpc.reflect

import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import com.google.inject.Provides
import com.squareup.wire.Service
import com.squareup.wire.WireRpc
import com.squareup.wire.reflector.SchemaReflector
import com.squareup.wire.schema.Location
import com.squareup.wire.schema.Pruner
import com.squareup.wire.schema.PruningRules
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
import java.util.regex.Pattern
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
    val sourceLocations = toSourceLocations(fileSystem, webActions)
    schemaLoader.initRoots(
      sourcePath = sourceLocations.keys().toList(),
      protoPath = listOf(Location.get("."))
    )
    schemaLoader.loadExhaustively = true
    val completeSchema = schemaLoader.loadSchema()
    return prune(completeSchema, sourceLocations.values())
  }

  private fun prune(completeSchema: Schema, keepPaths: Collection<String>): Schema {
    // Match a string like "/routeguide.RouteGuide/GetFeature" to extract "routeguide.RouteGuide"
    val urlPathPattern = Pattern.compile("/([^/]+)/([^/]+)")
    val serviceNamesToKeep = keepPaths.mapNotNull {
      val matcher = urlPathPattern.matcher(it)
      if (!matcher.matches()) return@mapNotNull null
      matcher.group(1)
    }.toSet()

    val pruningRules = PruningRules.Builder()
    for (protoFile in completeSchema.protoFiles) {
      for (service in protoFile.services) {
        if (service.type.toString() !in serviceNamesToKeep) {
          pruningRules.prune(service.type.toString())
        }
      }
    }

    return Pruner(completeSchema, pruningRules.build()).prune()
  }

  /**
   * Returns a map whose keys are the locations of .proto files declaring services, and whose values
   * are the URL paths served by those services.
   *
   * A typical result looks like this:
   *
   * ```
   * {
   *   "./grpc/reflection/v1alpha/reflection.proto": [
   *     "/grpc.reflection.v1alpha.ServerReflection/ServerReflectionInfo",
   *   ],
   *   "./routeguide/RouteGuideProto.proto": [
   *     "/routeguide.RouteGuide/GetFeature",
   *     "/routeguide.RouteGuide/ListFeatures",
   *     "/routeguide.RouteGuide/RecordRoute",
   *     "/routeguide.RouteGuide/RouteChat"
   *   ]
   * }
   * ```
   */
  @OptIn(ExperimentalFileSystem::class)
  private fun toSourceLocations(
    fileSystem: FileSystem,
    webActions: List<WebActionEntry>
  ): Multimap<Location, String> {
    val result = LinkedHashMultimap.create<Location, String>()
    for (webAction in webActions) {
      val wireRpcAnnotation = getWireRpcAnnotation(webAction.actionClass) ?: continue
      val sourceFile = wireRpcAnnotation.sourceFile
      if (sourceFile == "") continue // Generated before @WireRpc.sourceFile existed.
      result.put(Location.get(".", sourceFile), wireRpcAnnotation.path)
    }
    val iterator = result.keySet().iterator()
    while (iterator.hasNext()) {
      val location = iterator.next()
      val fileDoesNotExist = !fileSystem.exists(location.path.toPath())
      if (fileDoesNotExist) {
        logger.info("Omitting ${location.path} from ServerReflectionApi; file is not in artifact")
        iterator.remove()
      }
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
