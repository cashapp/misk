package misk.wasm

import io.github.charlietap.chasm.embedding.dsl.imports
import io.github.charlietap.chasm.embedding.shapes.Import
import io.github.charlietap.chasm.embedding.shapes.Store
import io.github.charlietap.chasm.executor.runtime.value.NumberValue

class MiskBridge {

  fun imports(store: Store): List<Import> {
    return imports(store) {
      // @WasmImport("misk_bridge", "put_string")
      // private external fun putStringExternal(address: Int): Int
      function {
        moduleName = "misk_bridge"
        entityName = "put_string"
        type {
          params {
            i32()
          }
          results {
            i32()
          }
        }
        reference { parameters ->
          println(this.store)
          println(this.instance)
          (parameters[0] as NumberValue.I32).value

          for (parameter in parameters) {
            println(parameter)
          }

          listOf(
            NumberValue.I32(99)
          )
        }
      }

      // @WasmImport("misk_bridge", "take_string")
      // private external fun takeStringExternal(id: Int, address: Int)
      function {
        moduleName = "misk_bridge"
        entityName = "take_string"
        type {
          params {
            i32()
            i32()
          }
          results {
          }
        }
        reference { parameters ->
          println(this.store)
          println(this.instance)
          for (parameter in parameters) {
            println(parameter)
          }

          listOf(
          )
        }
      }
    }
  }
}
