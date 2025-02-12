package misk.wasm

@WasmExport
fun greet() {
  Greeter().greet()
}

class Greeter {
  fun greet() = "Hello World"
}
