package misk.io

import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

/** @return all of the paths beneath this one, including nested paths */
fun Path.listRecursively(includeDirs: Boolean = false): List<Path> {
  val results = mutableListOf<Path>()
  listRecursively(includeDirs, results)
  return results.toList()
}

private fun Path.listRecursively(
    includeDirs: Boolean,
    results: MutableList<Path>
) {
  val (dirs, files) = Files.list(this).asSequence().partition { Files.isDirectory(it) }
  results.addAll(files)
  if (includeDirs) {
    results.addAll(dirs)
  }
  dirs.forEach { it.listRecursively(includeDirs, results) }
}
