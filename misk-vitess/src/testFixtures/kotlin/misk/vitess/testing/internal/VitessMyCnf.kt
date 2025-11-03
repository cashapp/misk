package misk.vitess.testing.internal

import misk.vitess.testing.TransactionIsolationLevel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class VitessMyCnf(
  containerName: String,
  sqlMode: String,
  transactionIsolationLevel: TransactionIsolationLevel,
) {
  val optionsFilePath: Path

  init {
    val tempDir: Path = Files.createTempDirectory(containerName)
    val optionsFileContent =
      """
          [mysqld]
          default-time-zone = '+00:00'
          sql_mode='$sqlMode'
          transaction-isolation='${transactionIsolationLevel.value}'
      """
        .trimIndent()
    val optionsFile = tempDir.resolve("my.cnf")
    optionsFilePath = Files.write(optionsFile, optionsFileContent.toByteArray(), StandardOpenOption.CREATE)
  }
}
