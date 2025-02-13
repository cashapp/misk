package misk.wasm

import io.github.charlietap.chasm.config.ModuleConfig
import io.github.charlietap.chasm.config.RuntimeConfig
import io.github.charlietap.chasm.embedding.error.ChasmError
import io.github.charlietap.chasm.embedding.instance
import io.github.charlietap.chasm.embedding.invoke
import io.github.charlietap.chasm.embedding.module
import io.github.charlietap.chasm.embedding.shapes.ChasmResult
import io.github.charlietap.chasm.embedding.shapes.Module
import io.github.charlietap.chasm.embedding.shapes.expect
import io.github.charlietap.chasm.embedding.store
import io.github.charlietap.chasm.stream.SourceReader
import okio.BufferedSource
import okio.Closeable
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import org.junit.jupiter.api.Test

class WasmSampleTest {
  private val fileSystem = FileSystem.SYSTEM
  // I changed this as it had a reference to a dir called "optimized" which doesn't exist in my build
  private val wasmFile =
    "build/compileSync/wasmWasi/main/developmentExecutable/kotlin/misk-misk-wasm-wasm-wasi.wasm".toPath()

  @Test
  fun test() {
    val bridge = MiskBridge()
    val module = fileSystem.readModule(wasmFile).expect("read success")

    for (export in module.exports) {
      println(export)
    }

    val store = store()
    val instance = instance(
      store = store,
      module = module,
      imports = bridge.imports(store) + wasiImports(store),
      config = RuntimeConfig.default(),
    ).expect("failed to instantiate module")

    invoke(store, instance, "_initialize")
    val result = invoke(store, instance, "greet").expect("Failed to invoke greet")

    println(result)
  }

}
fun FileSystem.readModule(path: Path) =
  source(path).buffer().use { it.readModule() }

fun BufferedSource.readModule(
  config: ModuleConfig = ModuleConfig.default(),
): ChasmResult<Module, ChasmError.DecodeError> =
  BufferedSourceReader(this).use { module(it, config) }

private class BufferedSourceReader(
  private val source: BufferedSource,
) : SourceReader, Closeable by source {
  override fun byte() = source.readByte()

  override fun bytes(amount: Int) = source.readByteArray(amount.toLong())

  override fun exhausted() = source.exhausted()

  override fun peek() = BufferedSourceReader(source.peek())
}

