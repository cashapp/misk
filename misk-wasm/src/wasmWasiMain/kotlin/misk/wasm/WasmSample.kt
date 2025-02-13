@file:OptIn(UnsafeWasmMemoryApi::class)
package misk.wasm

import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

@WasmExport
fun greet(stringId: Int, stringLength: Int): Int {
  val name = takeString(stringId, stringLength)
  val greeting = Greeter().greet(name)
  return putString(greeting)
}

class Greeter {
  fun greet(name: String) = "Hello, $name!"
}

private fun putString(string: String): Int {
  withScopedMemoryAllocator { allocator ->
    val data = string.encodeToByteArray()

    val stringPointer = allocator.allocate(data.size)

    for (i in data.indices) {
      (stringPointer + i).storeByte(data[i])
    }

    return putStringExternal(stringPointer.address.toInt())
  }
}

@WasmImport("misk_bridge", "put_string")
private external fun putStringExternal(address: Int): Int

private fun takeString(stringId: Int, stringLength: Int): String {
  withScopedMemoryAllocator { allocator ->
    val stringPointer = allocator.allocate(stringLength)
    takeStringExternal(stringId, stringPointer.address.toInt())

    val data = ByteArray(stringLength)
    for (i in 0 until stringLength) {
      data[i] = (stringPointer + i).loadByte()
    }
    return data.decodeToString()
  }
}

@WasmImport("misk_bridge", "take_string")
private external fun takeStringExternal(id: Int, address: Int)
