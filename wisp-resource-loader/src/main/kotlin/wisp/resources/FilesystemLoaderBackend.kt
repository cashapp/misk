package wisp.resources

import okio.BufferedSource
import okio.buffer
import okio.source
import wisp.logging.getLogger
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import java.nio.file.WatchService

/**
 * Read-only resources that are fetched from the local filesystem using absolute paths.
 *
 * This uses the scheme `filesystem:`.
 */
object FilesystemLoaderBackend : ResourceLoader.Backend() {

  private val logger = getLogger<FilesystemLoaderBackend>()

  const val SCHEME = "filesystem:"

  /*
   * Individual files can't be watched, it has to be at the directory level
   * So we track the watchers on directories and the file(s) we are wanting to watch in each one.
   */

  private val watcher: WatchService = FileSystems.getDefault().newWatchService()
  private val threadGroup = ThreadGroup("FilesystemLoader")

  // for testing access
  internal val watchedDirectoryThreads = mutableMapOf<Path, Thread>()
  private val watchedDirectoryPathCount = mutableMapOf<Path, Int>()
  private val watchedPaths = mutableSetOf<Path>()
  private var resourceChangedListeners = mutableMapOf<Path, (address: String) -> Unit>()

  private val watchedEventKinds = listOf(
    StandardWatchEventKinds.ENTRY_CREATE,
    StandardWatchEventKinds.ENTRY_MODIFY,
    StandardWatchEventKinds.ENTRY_DELETE
  )

  override fun open(path: String): BufferedSource? {
    val file = File(path)
    return try {
      file.source().buffer()
    } catch (e: FileNotFoundException) {
      null
    }
  }

  override fun exists(path: String) = File(path).exists()

  /**
   * For changes to the file that have been done externally, since this is a read-only
   * [ResourceLoader].
   */
  override fun watch(path: String, resourceChangedListener: (address: String) -> Unit) {

    val file = Paths.get(path)
    watchedPaths.add(file)
    resourceChangedListeners[file] = resourceChangedListener

    // watching files is done at the directory level, the parent of the file
    val directory = file.parent
    watchedDirectoryPathCount[directory] = watchedDirectoryPathCount[directory]?.plus(1) ?: 1
    if (watchedDirectoryThreads[directory]?.isAlive == true) {
      // already setup and running, so we can leave
      return
    }

    logger.info { "Registering watcher on $directory" }
    directory.register(
      watcher,
      watchedEventKinds.toTypedArray()
    )

    watchedDirectoryThreads[directory] = startWatcherThread(directory)
  }

  private fun startWatcherThread(
    directory: Path
  ): Thread {
    val thread = Thread(threadGroup) {
      try {
        while (true) {
          val key = watcher.take()
          // allow time to collect multiple quick changes
          //Thread.sleep(2000)
          key.pollEvents()
            .firstOrNull { event ->
              event.kind() in watchedEventKinds
            }
            ?.let { event ->
              val ev: WatchEvent<Path> = event as WatchEvent<Path>
              val fileName: Path = ev.context()
              val fullPathFile = directory.resolve(fileName)
              // we only care about changes in files we are watching
              if (fullPathFile in watchedPaths) {
                val address = SCHEME + fullPathFile.toString()
                resourceChangedListeners[fullPathFile]?.invoke(address)
              }
            }
          key.reset()
        }
      } catch (ie: InterruptedException) {
        // just exit quietly
      } catch (e: Exception) {
        logger.warn("Problem while watching $directory", e)
      }
    }

    thread.isDaemon = true
    thread.start()
    return thread
  }

  override fun unwatch(path: String) {
    val file = Paths.get(path)
    val directory = file.parent
    val newDirectoryPathCount = watchedDirectoryPathCount[directory]?.minus(1) ?: 0
    watchedDirectoryPathCount[directory] = newDirectoryPathCount
    watchedDirectoryThreads[directory]?.let {
      if (it.isAlive) {
        // stop the thread if we are not watching any files in this directory
        if (newDirectoryPathCount == 0) {
          logger.info { "Removing watcher on $directory" }
          it.interrupt()
          watchedDirectoryPathCount.remove(directory)
          watchedDirectoryThreads.remove(directory)
        }
      }
    }
    watchedPaths.remove(file)
    resourceChangedListeners.remove(file)
  }

}
