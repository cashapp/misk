package misk.embedded

import com.google.inject.Injector
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.net.URLClassLoader
import java.util.jar.JarFile

/**
 * Loads a Misk Service in the scope of its own class loader such that it can be accessed by in
 * another JVM project while avoiding classpath collisions on dependencies. The embedded misk
 * service is accessed by the host project using an "embedding interface". Use this embedding
 * interface to create a new embedded misk service:
 * ```
 * val embeddedServiceInstance = EmbeddedMisk.create<EmbeddedSampleService>()
 * ```
 *
 * The returned instance is the single access point for the host project to call into the embedded
 * misk service.
 */
object EmbeddedMisk {
  /**
   * This is the Application Loader and loads all host project classes and classes depended on by
   * the embedding interface.
   */
  private var hostLoader: ClassLoader = EmbeddedMisk::class.java.classLoader

  inline fun <reified T : Any> create() = create(T::class.java)

  fun <T : Any> create(embeddingInterfaceType: Class<T>): T {
    val classLoader = createIsolatedLoader(embeddingInterfaceType)
    val injector = createInjector(classLoader, embeddingInterfaceType)
    return injector.getInstance(embeddingInterfaceType)!!
  }

  private fun <T : Any> createIsolatedLoader(embeddingInterfaceType: Class<T>): URLClassLoader {
    val embeddingName = embeddingInterfaceType.name
    val extractedJarPath = copyAndExtractJar(embeddingInterfaceType)
    val jarFile = JarFile(extractedJarPath)

    // Create a routing loader that sends all classes in the jarFile to the isolated loader. This
    // loader's parent is the common loader used by the host project.
    val routingLoader = object : RoutingClassLoader("RoutingLoader/$embeddingName", hostLoader) {
      override fun shouldRouteToChild(className: String) =
        jarFile.getJarEntry(className.toFilePath()) != null
    }

    // Create an isolated class loader with the routing loader as a parent. This loader will
    // delegate to its parent, the routing loader. The routing loader will then route up to its own
    // parent OR back down to this isolated loader.
    val sourceUrls = arrayOf(extractedJarPath.toURI().toURL())
    return URLClassLoader("IsolatingLoader/$embeddingName", sourceUrls, routingLoader)
  }

  private fun <T : Any> createInjector(
    classLoader: URLClassLoader,
    embeddingInterface: Class<T>
  ): Injector {
    val injectorFactoryClass = classLoader.loadClass("${embeddingInterface.name}InjectorFactory")
    val injectorFactoryConstructor = injectorFactoryClass.constructors.single()
    val injectorFactory = injectorFactoryConstructor.newInstance()
    val createInjectorMethod = injectorFactoryClass.getDeclaredMethod("createInjector")
    return createInjectorMethod.invoke(injectorFactory) as Injector
  }

  private fun <T : Any> copyAndExtractJar(embeddingInterfaceType: Class<T>): File {
    val extractedJarPath = File.createTempFile(embeddingInterfaceType.name, "jar")
    extractedJarPath.deleteOnExit()

    EmbeddedMisk::class.java.getResourceAsStream("/${embeddingInterfaceType.name}.jar")
        .source().buffer().use { jarIn ->
          extractedJarPath.sink().use { tempOut ->
            jarIn.readAll(tempOut)
          }
        }
    return extractedJarPath
  }

  private fun String.toFilePath() = replace('.', '/') + ".class"
}

